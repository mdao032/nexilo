package com.nexilo.processing.dto;

import com.nexilo.processing.entity.ProcessingJob;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProcessingMapper {
    /**
     * Map une entité ProcessingJob vers son DTO de réponse.
     *
     * @param job l'entité source
     * @return l'objet de réponse destiné au client
     */
    ProcessingResponse toResponse(ProcessingJob job);

    /**
     * Transforme une requête entrante en une nouvelle entité ProcessingJob prête à être persistée.
     * Le statut initial est défini sur PENDING par défaut.
     *
     * @param request la requête client à convertir
     * @return l'entité ProcessingJob générée
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "resultData", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "jobType", source = "type", defaultValue = "SUMMARY")
    @Mapping(target = "inputData", source = "name")
    ProcessingJob toEntity(ProcessingRequest request);
}
