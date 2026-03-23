package com.nexilo.processing.controller;

import com.nexilo.processing.dto.ProcessingRequest;
import com.nexilo.processing.dto.ProcessingResponse;
import com.nexilo.processing.service.ProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/processing")
@RequiredArgsConstructor
@Tag(name = "Processing", description = "Background processing job management")
public class ProcessingController {

    private final ProcessingService processingService;

    @PostMapping
    @Operation(summary = "Create a new processing job")
    public ResponseEntity<ProcessingResponse> createJob(@Valid @RequestBody ProcessingRequest request) {
        return new ResponseEntity<>(processingService.createJob(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get job details by ID")
    public ResponseEntity<ProcessingResponse> getJob(@PathVariable Long id) {
        return ResponseEntity.ok(processingService.getJob(id));
    }

    @GetMapping
    @Operation(summary = "Get all processing jobs")
    public ResponseEntity<List<ProcessingResponse>> getAllJobs() {
        return ResponseEntity.ok(processingService.getAllJobs());
    }
}

