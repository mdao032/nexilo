package com.nexilo.document.dto;

import com.nexilo.document.entity.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {
    /**
     * Convertit une entité Document en son objet de transfert (DTO) pour la réponse.
     *
     * @param document l'entité source
     * @return le DTO contenant les informations du document
     */
    DocumentResponse toResponse(Document document);

    /**
     * Convertit une requête d'entrée en une nouvelle entité Document.
     * Ignore les champs auto-générés comme l'id et les dates.
     *
     * @param request la requête contenant les opérations à mapper
     * @return la nouvelle entité Document
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Document toEntity(DocumentRequest request);
}
