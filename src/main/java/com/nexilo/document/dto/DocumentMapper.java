package com.nexilo.document.dto;

import com.nexilo.document.entity.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {
    DocumentResponse toResponse(Document document);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Document toEntity(DocumentRequest request);
}

