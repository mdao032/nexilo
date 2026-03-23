package com.nexilo.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRequest {
    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    
    // In a real scenario, this might include MultipartFile or a path reference
    @NotBlank(message = "Path is required")
    private String path;
    
    private String contentType;
    
    private Long size;
}

