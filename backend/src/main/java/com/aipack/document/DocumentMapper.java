package com.aipack.document;

import com.aipack.document.dto.DocumentExtractionResponse;
import com.aipack.document.dto.DocumentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "companyId", source = "company.id")
    DocumentResponse toResponse(Document document);

    DocumentExtractionResponse toExtractionResponse(DocumentExtraction extraction);
}
