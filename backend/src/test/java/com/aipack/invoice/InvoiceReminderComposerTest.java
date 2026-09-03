package com.aipack.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.aipack.customer.Customer;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvoiceReminderComposerTest {

    private final InvoiceReminderComposer composer = new InvoiceReminderComposer();

    @Test
    void cordialReminderAtJ3() {
        InvoiceReminderComposer.ComposedReminder reminder = composer.compose(invoice(), 3);
        assertThat(reminder.subject()).contains("Rappel — facture DEMO-1");
        assertThat(reminder.body()).contains("Client Démo");
        assertThat(reminder.body()).contains("120");
        assertThat(reminder.body()).contains("28/08/2026");
    }

    @Test
    void firmReminderAtJ15() {
        InvoiceReminderComposer.ComposedReminder reminder = composer.compose(invoice(), 15);
        assertThat(reminder.subject()).contains("Rappel ferme");
    }

    @Test
    void manualAlertAtJ30() {
        InvoiceReminderComposer.ComposedReminder reminder = composer.compose(invoice(), 30);
        assertThat(reminder.subject()).contains("Alerte manuelle");
        assertThat(reminder.body()).contains("action manuelle");
    }

    private static Invoice invoice() {
        Customer customer = new Customer();
        customer.setName("Client Démo");
        Invoice invoice = new Invoice();
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber("DEMO-1");
        invoice.setDueDate(LocalDate.of(2026, 8, 28));
        invoice.setAmountIncludingTax(new BigDecimal("120.00"));
        invoice.setCurrency("EUR");
        return invoice;
    }
}
