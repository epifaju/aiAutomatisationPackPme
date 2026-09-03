package com.aipack.customer;

import com.aipack.customer.dto.CustomerResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(target = "companyId", source = "company.id")
    CustomerResponse toResponse(Customer customer);
}
