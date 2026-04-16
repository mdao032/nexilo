package com.nexilo.document.service;

import com.nexilo.document.dto.DocumentRequest;
import com.nexilo.document.dto.DocumentResponse;

import java.util.List;

public interface DocumentService {
    
    /**
     * Crée un nouveau document.
     *
     * @param request la requête contenant les propriétés du document
     * @return les données du document créé
     */
    DocumentResponse createDocument(DocumentRequest request);

    /**
     * Lit les données d'un document spécifique.
     *
     * @param id l'identifiant du document à lire
     * @return les données du document trouvé
     */
    DocumentResponse getDocument(Long id);

    /**
     * Récupère la liste intégrale des documents existants.
     *
     * @return la liste de tous les documents
     */
    List<DocumentResponse> getAllDocuments();

    /**
     * Modifie un document existant.
     *
     * @param id l'identifiant du document à modifier
     * @param request les nouvelles propriétés à appliquer
     * @return les nouvelles données du document mis à jour
     */
    DocumentResponse updateDocument(Long id, DocumentRequest request);

    /**
     * Supprime de manière permanente un document.
     *
     * @param id l'identifiant du document à supprimer
     */
    void deleteDocument(Long id);
}
