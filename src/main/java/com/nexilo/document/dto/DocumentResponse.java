package com.nexilo.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private Long id;
    private String title;
    private String description;
    private String path;
    private String contentType;
    private Long size;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

