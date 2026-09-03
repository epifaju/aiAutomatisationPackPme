package com.aipack.invoice;

import com.aipack.invoice.dto.CustomerSummary;
import com.aipack.invoice.dto.InvoiceReminderResponse;
import com.aipack.invoice.dto.InvoiceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InvoiceMapper {

    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "customer", source = "customer")
    @Mapping(target = "daysOverdue", ignore = true)
    @Mapping(target = "autoSendEnabled", ignore = true)
    InvoiceResponse toResponse(Invoice invoice);

    @Mapping(target = "id", source = "id")
    CustomerSummary toCustomerSummary(com.aipack.customer.Customer customer);

    InvoiceReminderResponse toReminderResponse(InvoiceReminder reminder);
}
