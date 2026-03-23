package com.nexilo.processing.service;

import com.nexilo.common.exception.ResourceNotFoundException;
import com.nexilo.processing.dto.ProcessingMapper;
import com.nexilo.processing.dto.ProcessingRequest;
import com.nexilo.processing.dto.ProcessingResponse;
import com.nexilo.processing.entity.ProcessingJob;
import com.nexilo.processing.repository.ProcessingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcessingServiceImpl implements ProcessingService {

    private final ProcessingRepository processingRepository;
    private final ProcessingMapper processingMapper;

    @Override
    @Transactional
    public ProcessingResponse createJob(ProcessingRequest request) {
        ProcessingJob job = processingMapper.toEntity(request);
        job = processingRepository.save(job);
        return processingMapper.toResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingResponse getJob(Long id) {
        ProcessingJob job = processingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processing Job not found with id: " + id));
        return processingMapper.toResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessingResponse> getAllJobs() {
        return processingRepository.findAll().stream()
                .map(processingMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProcessingResponse updateJobStatus(Long id, String status) {
        ProcessingJob job = processingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processing Job not found with id: " + id));
        
        try {
            job.setStatus(ProcessingJob.JobStatus.valueOf(status.toUpperCase()));
        } catch (IllegalArgumentException e) {
             throw new IllegalArgumentException("Invalid status: " + status);
        }
        
        job = processingRepository.save(job);
        return processingMapper.toResponse(job);
    }
}

