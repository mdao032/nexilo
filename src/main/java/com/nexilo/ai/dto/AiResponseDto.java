package com.nexilo.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResponseDto {
    private Long id;
    private String prompt;
    private String response;
    private String model;
    private LocalDateTime createdAt;
}

