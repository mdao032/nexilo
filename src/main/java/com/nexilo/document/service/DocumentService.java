package com.nexilo.document.service;

import com.nexilo.document.dto.DocumentRequest;
import com.nexilo.document.dto.DocumentResponse;

import java.util.List;

public interface DocumentService {
    DocumentResponse createDocument(DocumentRequest request);
    DocumentResponse getDocument(Long id);
    List<DocumentResponse> getAllDocuments();
    DocumentResponse updateDocument(Long id, DocumentRequest request);
    void deleteDocument(Long id);
}

