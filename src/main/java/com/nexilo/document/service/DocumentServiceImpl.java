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

    @Override
    @Transactional
    public DocumentResponse createDocument(DocumentRequest request) {
        Document document = documentMapper.toEntity(request);
        Document savedDocument = documentRepository.save(document);
        return documentMapper.toResponse(savedDocument);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        return documentMapper.toResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAll().stream()
                .map(documentMapper::toResponse)
                .collect(Collectors.toList());
    }

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

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        if (!documentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Document not found with id: " + id);
        }
        documentRepository.deleteById(id);
    }
}

