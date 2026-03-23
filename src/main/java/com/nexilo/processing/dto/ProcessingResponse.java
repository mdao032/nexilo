package com.nexilo.processing.dto;

import com.nexilo.processing.entity.ProcessingJob.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingResponse {
    private Long id;
    private String jobType;
    private JobStatus status;
    private String resultData;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

