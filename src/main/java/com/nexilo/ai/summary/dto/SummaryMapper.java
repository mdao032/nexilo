package com.nexilo.ai.summary.dto;

import com.nexilo.ai.summary.entity.Summary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct pour convertir les entités Summary en DTO SummaryResponse.
 */
@Mapper(componentModel = "spring")
public interface SummaryMapper {

    /**
     * Convertit un {@link Summary} en {@link SummaryResponse} (résumé généré → non caché).
     */
    @Mapping(target = "summaryId",    source = "summary.id")
    @Mapping(target = "documentId",   source = "summary.document.id")
    @Mapping(target = "fileName",     source = "summary.document.fileName")
    @Mapping(target = "fileSize",     source = "summary.document.fileSize")
    @Mapping(target = "content",      source = "summary.content")
    @Mapping(target = "language",     source = "summary.language")
    @Mapping(target = "model",        source = "summary.model")
    @Mapping(target = "tokensUsed",   source = "summary.tokensUsed")
    @Mapping(target = "createdAt",    source = "summary.createdAt")
    @Mapping(target = "cached",       source = "cached")
    SummaryResponse toResponse(Summary summary, boolean cached);
}

