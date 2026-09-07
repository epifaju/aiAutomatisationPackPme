package com.aipack.email;

import com.aipack.email.dto.EmailAnalysisResponse;
import com.aipack.email.dto.EmailResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmailMapper {

    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "autoSendEnabled", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    EmailResponse toResponse(Email email);

    EmailAnalysisResponse toAnalysisResponse(EmailAnalysis analysis);
}
