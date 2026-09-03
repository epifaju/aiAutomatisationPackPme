package com.aipack.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class InvoiceReminderComposer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ComposedReminder compose(Invoice invoice, int level) {
        String customerName = invoice.getCustomer() == null ? "Madame, Monsieur" : invoice.getCustomer().getName();
        String number = invoice.getInvoiceNumber();
        String due = invoice.getDueDate() == null ? "" : invoice.getDueDate().format(DATE);
        String amount = formatMoney(invoice.getAmountIncludingTax()) + " " + nullToEur(invoice.getCurrency());
        return switch (level) {
            case 3 -> new ComposedReminder(
                    "Rappel — facture " + number,
                    greeting(customerName)
                            + "Sauf erreur de notre part, la facture "
                            + number
                            + " d'un montant de "
                            + amount
                            + " était due le "
                            + due
                            + ".\n\n"
                            + "Pourriez-vous, s'il vous plaît, procéder au règlement ou nous indiquer si un paiement est déjà en cours ?\n\n"
                            + "Cordialement");
            case 7 -> new ComposedReminder(
                    "Deuxième rappel — facture " + number,
                    greeting(customerName)
                            + "Nous nous permettons de revenir vers vous concernant la facture "
                            + number
                            + " ("
                            + amount
                            + "), échue le "
                            + due
                            + ".\n\n"
                            + "Merci de régulariser cette situation dans les meilleurs délais.\n\n"
                            + "Cordialement");
            case 15 -> new ComposedReminder(
                    "Rappel ferme — facture " + number,
                    greeting(customerName)
                            + "Malgré nos précédents échanges, la facture "
                            + number
                            + " d'un montant de "
                            + amount
                            + " reste impayée (échéance "
                            + due
                            + ").\n\n"
                            + "Nous vous demandons de procéder au règlement sans délai.\n\n"
                            + "Cordialement");
            default -> new ComposedReminder(
                    "Alerte manuelle — facture " + number + " en retard",
                    greeting(customerName)
                            + "La facture "
                            + number
                            + " ("
                            + amount
                            + ") est en retard important (échéance "
                            + due
                            + ").\n\n"
                            + "Cette relance nécessite une action manuelle de votre part.\n\n"
                            + "Cordialement");
        };
    }

    private static String greeting(String name) {
        return "Bonjour " + name + ",\n\n";
    }

    private static String formatMoney(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        return NumberFormat.getNumberInstance(Locale.FRANCE).format(value);
    }

    private static String nullToEur(String currency) {
        return currency == null || currency.isBlank() ? "EUR" : currency;
    }

    public record ComposedReminder(String subject, String body) {}
}
