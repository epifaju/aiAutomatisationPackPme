package com.aipack.customer;

import com.aipack.customer.dto.CreateCustomerRequest;
import com.aipack.customer.dto.CustomerResponse;
import com.aipack.customer.dto.UpdateCustomerRequest;
import com.aipack.identity.Company;
import com.aipack.invoice.InvoiceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final CustomerMapper customerMapper;
    private final EntityManager entityManager;

    public CustomerService(
            CustomerRepository customerRepository,
            InvoiceRepository invoiceRepository,
            CustomerMapper customerMapper,
            EntityManager entityManager) {
        this.customerRepository = customerRepository;
        this.invoiceRepository = invoiceRepository;
        this.customerMapper = customerMapper;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(UUID companyId, String q, Pageable pageable) {
        String query = blankToNull(q);
        Specification<Customer> spec = (root, ignored, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (query != null) {
                String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return customerRepository.findAll(spec, pageable).map(customerMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID companyId, UUID id) {
        return customerMapper.toResponse(require(companyId, id));
    }

    @Transactional
    public CustomerResponse create(UUID companyId, CreateCustomerRequest request) {
        Customer customer = new Customer();
        customer.setCompany(entityManager.getReference(Company.class, companyId));
        customer.setName(request.name().trim());
        customer.setEmail(blankToNull(request.email()));
        customer.setPhone(blankToNull(request.phone()));
        customer.setAddress(blankToNull(request.address()));
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(UUID companyId, UUID id, UpdateCustomerRequest request) {
        Customer customer = require(companyId, id);
        if (blankToNull(request.name()) != null) {
            customer.setName(request.name().trim());
        }
        if (request.email() != null) {
            customer.setEmail(blankToNull(request.email()));
        }
        if (request.phone() != null) {
            customer.setPhone(blankToNull(request.phone()));
        }
        if (request.address() != null) {
            customer.setAddress(blankToNull(request.address()));
        }
        return customerMapper.toResponse(customer);
    }

    @Transactional
    public void delete(UUID companyId, UUID id) {
        Customer customer = require(companyId, id);
        if (invoiceRepository.countByCustomer_Id(id) > 0) {
            throw CustomerException.inUse();
        }
        customerRepository.delete(customer);
    }

    Customer require(UUID companyId, UUID id) {
        return customerRepository.findByIdAndCompany_Id(id, companyId).orElseThrow(CustomerException::notFound);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
