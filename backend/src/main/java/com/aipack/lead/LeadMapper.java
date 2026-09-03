package com.aipack.lead;

import com.aipack.lead.dto.LeadEventResponse;
import com.aipack.lead.dto.LeadResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LeadMapper {

    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "scoreBand", ignore = true)
    @Mapping(target = "events", ignore = true)
    LeadResponse toResponse(Lead lead);

    LeadEventResponse toEventResponse(LeadEvent event);
}
