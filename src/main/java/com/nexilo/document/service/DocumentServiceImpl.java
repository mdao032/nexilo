package com.nexilo.document.service;

import com.nexilo.common.exception.ResourceNotFoundException;
import com.nexilo.document.dto.DocumentMapper;
import com.nexilo.document.dto.DocumentRequest;
import com.nexilo.document.dto.DocumentResponse;
import com.nexilo.document.entity.Document;
import com.nexilo.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper;

    /**
     * Sauvegarde un nouveau document dans la base de données.
     * Convertit la requête en entité, la sauvegarde, et retourne le DTO associé.
     *
     * @param request les données de création du document
     * @return la réponse DTO détaillée du document créé
     */
    @Override
    @Transactional
    public DocumentResponse createDocument(DocumentRequest request) {
        Document document = documentMapper.toEntity(request);
        Document savedDocument = documentRepository.save(document);
        return documentMapper.toResponse(savedDocument);
    }

    /**
     * Récupère un document par son identifiant. 
     * Lance une exception si le document est introuvable.
     *
     * @param id l'identifiant du document attendu
     * @return la réponse DTO associée au document
     */
    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        return documentMapper.toResponse(document);
    }

    /**
     * Enumère tous les documents enregistrés dans le système.
     *
     * @return une liste modélisée en DTOs représentant l'ensemble des documents
     */
    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAll().stream()
                .map(documentMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Met à jour toutes les informations modifiables d'un document existant,
     * telles que son titre, sa description, son chemin, son type et sa taille.
     *
     * @param id      l'identifiant unique du document
     * @param request l'objet de requête encapsulant les nouvelles données
     * @return le document fraîchement mis à jour sous forme de DTO
     */
    @Override
    @Transactional
    public DocumentResponse updateDocument(Long id, DocumentRequest request) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));

        document.setTitle(request.getTitle());
        document.setDescription(request.getDescription());
        document.setPath(request.getPath());
        document.setContentType(request.getContentType());
        document.setSize(request.getSize());

        Document updatedDocument = documentRepository.save(document);
        return documentMapper.toResponse(updatedDocument);
    }

    /**
     * Supprime définitivement un document de la base.
     * Si le document n'existe pas, une exception est déclenchée.
     *
     * @param id l'identifiant du document à cibler pour la suppression
     */
    @Override
    @Transactional
    public void deleteDocument(Long id) {
        if (!documentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Document not found with id: " + id);
        }
        documentRepository.deleteById(id);
    }
}
