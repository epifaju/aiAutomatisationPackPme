package com.aipack.invoice;

import com.aipack.config.InvoiceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.invoices", name = "scheduler-enabled", havingValue = "true")
public class InvoiceOverdueScheduler {

    private final InvoiceService invoiceService;
    private final InvoiceProperties invoiceProperties;

    public InvoiceOverdueScheduler(InvoiceService invoiceService, InvoiceProperties invoiceProperties) {
        this.invoiceService = invoiceService;
        this.invoiceProperties = invoiceProperties;
    }

    @Scheduled(cron = "${app.invoices.scheduler-cron:0 0 8 * * *}", zone = "Europe/Paris")
    public void detectOverdue() {
        if (!invoiceProperties.schedulerEnabled()) {
            return;
        }
        invoiceService.detectOverdueForAllCompanies();
    }
}
