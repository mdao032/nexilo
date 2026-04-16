package com.nexilo.processing.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessingResultResponse {
    private Long jobId;
    private String status;
    private String summary;
}
