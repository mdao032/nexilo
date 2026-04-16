package com.nexilo.document.controller;

import com.nexilo.document.dto.DocumentRequest;
import com.nexilo.document.dto.DocumentResponse;
import com.nexilo.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Document management APIs")
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Crée un nouveau document.
     *
     * @param request la requête contenant les informations du document à créer
     * @return la réponse avec le document créé et le statut HTTP 201 (CREATED)
     */
    @PostMapping
    @Operation(summary = "Create a new document")
    public ResponseEntity<DocumentResponse> createDocument(@Valid @RequestBody DocumentRequest request) {
        return new ResponseEntity<>(documentService.createDocument(request), HttpStatus.CREATED);
    }

    /**
     * Récupère un document par son identifiant unique.
     *
     * @param id l'identifiant du document
     * @return le document demandé s'il est trouvé
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID")
    public ResponseEntity<DocumentResponse> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocument(id));
    }

    /**
     * Récupère la liste de tous les documents.
     *
     * @return la liste des documents disponibles
     */
    @GetMapping
    @Operation(summary = "Get all documents")
    public ResponseEntity<List<DocumentResponse>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    /**
     * Met à jour les informations d'un document existant.
     *
     * @param id l'identifiant du document à mettre à jour
     * @param request les nouvelles données du document
     * @return le document mis à jour
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update document")
    public ResponseEntity<DocumentResponse> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequest request) {
        return ResponseEntity.ok(documentService.updateDocument(id, request));
    }

    /**
     * Supprime un document par son identifiant.
     *
     * @param id l'identifiant du document à supprimer
     * @return une réponse vide avec le statut HTTP 204 (NO CONTENT)
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete document")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
