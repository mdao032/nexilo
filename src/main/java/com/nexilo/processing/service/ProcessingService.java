package com.nexilo.processing.service;

import com.nexilo.processing.dto.ProcessingRequest;
import com.nexilo.processing.dto.ProcessingResponse;

import java.util.List;

public interface ProcessingService {
    ProcessingResponse createJob(ProcessingRequest request);
    ProcessingResponse getJob(Long id);
    List<ProcessingResponse> getAllJobs();
    ProcessingResponse updateJobStatus(Long id, String status);
}

