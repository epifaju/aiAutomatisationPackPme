package com.aipack.invoice;

import com.aipack.audit.AuditRecord;
import com.aipack.audit.AuditService;
import com.aipack.audit.AuditStatus;
import com.aipack.config.InvoiceProperties;
import com.aipack.customer.Customer;
import com.aipack.customer.CustomerException;
import com.aipack.customer.CustomerRepository;
import com.aipack.email.OutboundMail;
import com.aipack.email.OutboundMailSender;
import com.aipack.identity.Company;
import com.aipack.identity.CompanyRepository;
import com.aipack.identity.CompanySettings;
import com.aipack.identity.CompanySettingsRepository;
import com.aipack.invoice.dto.CreateInvoiceRequest;
import com.aipack.invoice.dto.InvoiceResponse;
import com.aipack.invoice.dto.OverdueDetectionResponse;
import com.aipack.invoice.dto.UpdateInvoiceRequest;
import com.aipack.invoice.dto.WebhookInvoiceReminderRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    static final String WORKFLOW_INVOICE = "invoice";
    static final String WORKFLOW_OVERDUE = "invoice-overdue-detection";
    static final String WORKFLOW_REMINDER = "invoice-reminder";
    static final String ENTITY_TYPE = "INVOICE";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceReminderRepository reminderRepository;
    private final CustomerRepository customerRepository;
    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final InvoiceMapper invoiceMapper;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final OutboundMailSender outboundMailSender;
    private final InvoiceReminderComposer reminderComposer;
    private final InvoiceProperties invoiceProperties;
    private final Clock clock;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceReminderRepository reminderRepository,
            CustomerRepository customerRepository,
            CompanyRepository companyRepository,
            CompanySettingsRepository companySettingsRepository,
            InvoiceMapper invoiceMapper,
            AuditService auditService,
            EntityManager entityManager,
            OutboundMailSender outboundMailSender,
            InvoiceReminderComposer reminderComposer,
            InvoiceProperties invoiceProperties,
            Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.reminderRepository = reminderRepository;
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.invoiceMapper = invoiceMapper;
        this.auditService = auditService;
        this.entityManager = entityManager;
        this.outboundMailSender = outboundMailSender;
        this.reminderComposer = reminderComposer;
        this.invoiceProperties = invoiceProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> list(UUID companyId, String status, UUID customerId, String q, Pageable pageable) {
        String statusFilter = blankToNull(status) == null ? null : InvoiceStatus.from(status).name();
        String query = blankToNull(q);
        CompanySettings settings = settingsOf(companyId);
        LocalDate today = todayOf(companyId);
        Specification<Invoice> spec = (root, ignored, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (query != null) {
                String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("invoiceNumber")), like));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return invoiceRepository.findAll(spec, pageable).map(invoice -> toResponse(invoice, settings, today));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID companyId, UUID id) {
        return toResponse(requireInvoice(companyId, id), settingsOf(companyId), todayOf(companyId));
    }

    @Transactional
    public InvoiceResponse create(UUID companyId, CreateInvoiceRequest request) {
        Customer customer = requireCustomer(companyId, request.customerId());
        if (invoiceRepository.findByCompany_IdAndInvoiceNumber(companyId, request.invoiceNumber().trim()).isPresent()) {
            throw InvoiceException.duplicateNumber();
        }
        Invoice invoice = new Invoice();
        invoice.setCompany(entityManager.getReference(Company.class, companyId));
        invoice.setCustomer(customer);
        applyAmounts(
                invoice,
                request.invoiceNumber(),
                request.invoiceDate(),
                request.dueDate(),
                request.amountExcludingTax(),
                request.vat(),
                request.amountIncludingTax(),
                request.currency(),
                request.status() == null ? InvoiceStatus.DRAFT.name() : InvoiceStatus.from(request.status()).name());
        Invoice saved = saveInvoice(invoice);
        audit(
                companyId,
                WORKFLOW_INVOICE,
                "CREATED",
                saved.getId(),
                AuditStatus.SUCCESS,
                Map.of("invoiceNumber", saved.getInvoiceNumber(), "status", saved.getStatus()));
        return toResponse(saved, settingsOf(companyId), todayOf(companyId));
    }

    @Transactional
    public InvoiceResponse update(UUID companyId, UUID id, UpdateInvoiceRequest request) {
        Invoice invoice = requireInvoice(companyId, id);
        if (request.customerId() != null) {
            invoice.setCustomer(requireCustomer(companyId, request.customerId()));
        }
        if (blankToNull(request.invoiceNumber()) != null
                && !request.invoiceNumber().trim().equals(invoice.getInvoiceNumber())) {
            if (invoiceRepository.findByCompany_IdAndInvoiceNumber(companyId, request.invoiceNumber().trim()).isPresent()) {
                throw InvoiceException.duplicateNumber();
            }
        }
        applyAmounts(
                invoice,
                request.invoiceNumber() == null ? invoice.getInvoiceNumber() : request.invoiceNumber(),
                request.invoiceDate() == null ? invoice.getInvoiceDate() : request.invoiceDate(),
                request.dueDate() == null ? invoice.getDueDate() : request.dueDate(),
                request.amountExcludingTax() == null ? invoice.getAmountExcludingTax() : request.amountExcludingTax(),
                request.vat() == null ? invoice.getVat() : request.vat(),
                request.amountIncludingTax() == null ? invoice.getAmountIncludingTax() : request.amountIncludingTax(),
                request.currency() == null ? invoice.getCurrency() : request.currency(),
                request.status() == null ? invoice.getStatus() : InvoiceStatus.from(request.status()).name());
        Invoice saved = saveInvoice(invoice);
        audit(
                companyId,
                WORKFLOW_INVOICE,
                "UPDATED",
                saved.getId(),
                AuditStatus.SUCCESS,
                Map.of("status", saved.getStatus()));
        return toResponse(saved, settingsOf(companyId), todayOf(companyId));
    }

    @Transactional
    public OverdueDetectionResponse detectOverdue(UUID companyId, UUID invoiceId) {
        if (!companyRepository.existsById(companyId)) {
            throw InvoiceException.companyNotFound();
        }
        CompanySettings settings = settingsOf(companyId);
        LocalDate today = todayOf(companyId);
        List<Invoice> invoices;
        if (invoiceId != null) {
            invoices = List.of(requireInvoice(companyId, invoiceId));
        } else {
            invoices = invoiceRepository.findByCompany_IdAndStatusIn(
                    companyId, List.of(InvoiceStatus.SENT.name(), InvoiceStatus.OVERDUE.name()));
        }
        int markedOverdue = 0;
        int remindersCreated = 0;
        List<Invoice> touched = new ArrayList<>();
        for (Invoice invoice : invoices) {
            DetectionOutcome outcome = detectOne(invoice, settings, today);
            markedOverdue += outcome.markedOverdue() ? 1 : 0;
            remindersCreated += outcome.remindersCreated();
            if (outcome.markedOverdue() || outcome.remindersCreated() > 0) {
                touched.add(invoice);
            }
        }
        List<InvoiceResponse> responses = touched.stream().map(invoice -> toResponse(invoice, settings, today)).toList();
        return new OverdueDetectionResponse(markedOverdue, remindersCreated, responses);
    }

    @Transactional
    public OverdueDetectionResponse detectFromWebhook(WebhookInvoiceReminderRequest request) {
        return detectOverdue(request.companyId(), request.invoiceId());
    }

    @Transactional
    public void detectOverdueForAllCompanies() {
        for (Company company : companyRepository.findAll()) {
            detectOverdue(company.getId(), null);
        }
    }

    @Transactional
    public InvoiceResponse approveReminder(UUID companyId, UUID invoiceId, int level) {
        Invoice invoice = requireInvoice(companyId, invoiceId);
        InvoiceReminder reminder = requirePending(invoice, level);
        reminder.setStatus(InvoiceReminderStatus.APPROVED.name());
        audit(
                companyId,
                WORKFLOW_REMINDER,
                "APPROVED",
                invoice.getId(),
                AuditStatus.SUCCESS,
                Map.of("reminderLevel", level));
        CompanySettings settings = settingsOf(companyId);
        if (autoSendEnabled(settings) && !InvoiceOverdueCalculator.isManualAlert(level)) {
            return dispatchSend(invoice, reminder, settings, todayOf(companyId), true);
        }
        return toResponse(invoice, settings, todayOf(companyId));
    }

    @Transactional
    public InvoiceResponse rejectReminder(UUID companyId, UUID invoiceId, int level) {
        Invoice invoice = requireInvoice(companyId, invoiceId);
        InvoiceReminder reminder = requirePending(invoice, level);
        reminder.setStatus(InvoiceReminderStatus.REJECTED.name());
        audit(
                companyId,
                WORKFLOW_REMINDER,
                "REJECTED",
                invoice.getId(),
                AuditStatus.SUCCESS,
                Map.of("reminderLevel", level));
        return toResponse(invoice, settingsOf(companyId), todayOf(companyId));
    }

    @Transactional
    public InvoiceResponse sendReminder(UUID companyId, UUID invoiceId, int level) {
        Invoice invoice = requireInvoice(companyId, invoiceId);
        InvoiceReminder reminder = reminderRepository
                .findByInvoice_IdAndReminderLevel(invoice.getId(), InvoiceOverdueCalculator.requireAllowedLevel(level))
                .orElseThrow(InvoiceException::reminderNotFound);
        if (!InvoiceReminderStatus.APPROVED.name().equals(reminder.getStatus())) {
            throw InvoiceException.sendNotAllowed();
        }
        return dispatchSend(invoice, reminder, settingsOf(companyId), todayOf(companyId), false);
    }

    private DetectionOutcome detectOne(Invoice invoice, CompanySettings settings, LocalDate today) {
        InvoiceStatus status = InvoiceStatus.from(invoice.getStatus());
        if (status == InvoiceStatus.PAID || status == InvoiceStatus.CANCELLED || status == InvoiceStatus.DRAFT) {
            return DetectionOutcome.none();
        }
        if (!InvoiceOverdueCalculator.isPastDue(invoice.getDueDate(), today)) {
            return DetectionOutcome.none();
        }
        boolean markedOverdue = false;
        if (status == InvoiceStatus.SENT) {
            invoice.setStatus(InvoiceStatus.OVERDUE.name());
            markedOverdue = true;
            audit(
                    invoice.getCompany().getId(),
                    WORKFLOW_OVERDUE,
                    "OVERDUE",
                    invoice.getId(),
                    AuditStatus.SUCCESS,
                    Map.of("dueDate", invoice.getDueDate().toString()));
        }
        int days = InvoiceOverdueCalculator.daysOverdue(invoice.getDueDate(), today);
        int created = 0;
        for (int level : InvoiceOverdueCalculator.levelsDue(days, settings.getInvoiceReminderDays())) {
            if (reminderRepository.findByInvoice_IdAndReminderLevel(invoice.getId(), level).isPresent()) {
                continue;
            }
            InvoiceReminder reminder = new InvoiceReminder();
            reminder.setCompany(invoice.getCompany());
            reminder.setInvoice(invoice);
            reminder.setReminderLevel(level);
            reminder.setStatus(InvoiceReminderStatus.PENDING_APPROVAL.name());
            reminder.setScheduledAt(Instant.now(clock));
            InvoiceReminder saved = reminderRepository.save(reminder);
            invoice.getReminders().add(saved);
            created++;
            audit(
                    invoice.getCompany().getId(),
                    WORKFLOW_REMINDER,
                    "CREATED",
                    invoice.getId(),
                    AuditStatus.SUCCESS,
                    Map.of("reminderLevel", level, "daysOverdue", days));
            if (autoSendEnabled(settings) && !InvoiceOverdueCalculator.isManualAlert(level)) {
                saved.setStatus(InvoiceReminderStatus.APPROVED.name());
                dispatchSend(invoice, saved, settings, today, true);
            }
        }
        return new DetectionOutcome(markedOverdue, created);
    }

    private InvoiceResponse dispatchSend(
            Invoice invoice, InvoiceReminder reminder, CompanySettings settings, LocalDate today, boolean automatic) {
        InvoiceStatus status = InvoiceStatus.from(invoice.getStatus());
        if (status == InvoiceStatus.PAID) {
            audit(
                    invoice.getCompany().getId(),
                    WORKFLOW_REMINDER,
                    "EMAIL_SENT",
                    invoice.getId(),
                    AuditStatus.SKIPPED,
                    Map.of("reminderLevel", reminder.getReminderLevel(), "reason", "PAID"));
            throw InvoiceException.sendNotAllowed();
        }
        if (!status.allowsReminder()) {
            throw InvoiceException.sendNotAllowed();
        }
        if (InvoiceReminderStatus.EXECUTED.name().equals(reminder.getStatus())) {
            throw InvoiceException.alreadySent();
        }
        Customer customer = invoice.getCustomer();
        if (customer == null || blankToNull(customer.getEmail()) == null) {
            throw InvoiceException.missingCustomerEmail();
        }
        InvoiceReminderComposer.ComposedReminder composed =
                reminderComposer.compose(invoice, reminder.getReminderLevel());
        try {
            outboundMailSender.send(new OutboundMail(
                    invoiceProperties.fromAddressOrDefault(),
                    customer.getEmail(),
                    composed.subject(),
                    composed.body()));
        } catch (RuntimeException ex) {
            audit(
                    invoice.getCompany().getId(),
                    WORKFLOW_REMINDER,
                    "EMAIL_SENT",
                    invoice.getId(),
                    AuditStatus.ERROR,
                    Map.of("reminderLevel", reminder.getReminderLevel(), "automatic", automatic));
            throw InvoiceException.mailSendFailed();
        }
        reminder.setStatus(InvoiceReminderStatus.EXECUTED.name());
        reminder.setSentAt(Instant.now(clock));
        audit(
                invoice.getCompany().getId(),
                WORKFLOW_REMINDER,
                "EMAIL_SENT",
                invoice.getId(),
                AuditStatus.SUCCESS,
                Map.of("reminderLevel", reminder.getReminderLevel(), "automatic", automatic));
        return toResponse(invoice, settings, today);
    }

    private InvoiceReminder requirePending(Invoice invoice, int level) {
        InvoiceOverdueCalculator.requireAllowedLevel(level);
        if (!InvoiceStatus.from(invoice.getStatus()).allowsReminder()) {
            throw InvoiceException.reminderNotAllowed();
        }
        InvoiceReminder reminder = reminderRepository
                .findByInvoice_IdAndReminderLevel(invoice.getId(), level)
                .orElseThrow(InvoiceException::reminderNotFound);
        if (!InvoiceReminderStatus.PENDING_APPROVAL.name().equals(reminder.getStatus())) {
            throw InvoiceException.approvalNotAllowed();
        }
        return reminder;
    }

    private Invoice requireInvoice(UUID companyId, UUID id) {
        return invoiceRepository.findByIdAndCompany_Id(id, companyId).orElseThrow(InvoiceException::notFound);
    }

    private Customer requireCustomer(UUID companyId, UUID customerId) {
        return customerRepository.findByIdAndCompany_Id(customerId, companyId).orElseThrow(CustomerException::notFound);
    }

    private CompanySettings settingsOf(UUID companyId) {
        return companySettingsRepository.findByCompanyId(companyId).orElseGet(() -> {
            CompanySettings defaults = new CompanySettings();
            defaults.setInvoiceReminderDays(new Integer[] {3, 7, 15, 30});
            defaults.setInvoiceReminderAutoSend(false);
            return defaults;
        });
    }

    private boolean autoSendEnabled(CompanySettings settings) {
        return invoiceProperties.autoSend() && settings.isInvoiceReminderAutoSend();
    }

    private LocalDate todayOf(UUID companyId) {
        String timezone = companyRepository
                .findById(companyId)
                .map(Company::getTimezone)
                .filter(value -> value != null && !value.isBlank())
                .orElse("Europe/Paris");
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone);
        } catch (RuntimeException ex) {
            zone = ZoneId.of("Europe/Paris");
        }
        return LocalDate.now(clock.withZone(zone));
    }

    private InvoiceResponse toResponse(Invoice invoice, CompanySettings settings, LocalDate today) {
        InvoiceResponse base = invoiceMapper.toResponse(invoice);
        int days = InvoiceOverdueCalculator.daysOverdue(invoice.getDueDate(), today);
        return new InvoiceResponse(
                base.id(),
                invoice.getCompany().getId(),
                base.customer(),
                base.invoiceNumber(),
                base.invoiceDate(),
                base.dueDate(),
                base.amountExcludingTax(),
                base.vat(),
                base.amountIncludingTax(),
                base.currency(),
                base.status(),
                days,
                autoSendEnabled(settings),
                base.reminders(),
                base.createdAt(),
                base.updatedAt());
    }

    private void applyAmounts(
            Invoice invoice,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            BigDecimal amountExcludingTax,
            BigDecimal vat,
            BigDecimal amountIncludingTax,
            String currency,
            String status) {
        invoice.setInvoiceNumber(invoiceNumber.trim());
        invoice.setInvoiceDate(invoiceDate);
        invoice.setDueDate(dueDate);
        BigDecimal ht = amountExcludingTax == null ? BigDecimal.ZERO : amountExcludingTax;
        BigDecimal tax = vat == null ? BigDecimal.ZERO : vat;
        invoice.setAmountExcludingTax(ht);
        invoice.setVat(tax);
        invoice.setAmountIncludingTax(amountIncludingTax == null ? ht.add(tax) : amountIncludingTax);
        invoice.setCurrency(
                currency == null || currency.isBlank() ? "EUR" : currency.trim().toUpperCase(Locale.ROOT));
        invoice.setStatus(status);
    }

    private Invoice saveInvoice(Invoice invoice) {
        try {
            return invoiceRepository.saveAndFlush(invoice);
        } catch (DataIntegrityViolationException ex) {
            throw InvoiceException.duplicateNumber();
        }
    }

    private void audit(
            UUID companyId, String workflow, String action, UUID entityId, AuditStatus status, Map<String, Object> metadata) {
        auditService.record(new AuditRecord(
                companyId, workflow, action, ENTITY_TYPE, entityId.toString(), status, metadata));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record DetectionOutcome(boolean markedOverdue, int remindersCreated) {
        static DetectionOutcome none() {
            return new DetectionOutcome(false, 0);
        }
    }
}
