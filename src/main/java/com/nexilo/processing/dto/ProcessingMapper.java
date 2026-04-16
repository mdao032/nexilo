package com.nexilo.processing.dto;

import com.nexilo.processing.entity.ProcessingJob;
import com.nexilo.processing.entity.ProcessingJob.JobType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProcessingMapper {

    ProcessingResponse toResponse(ProcessingJob job);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "resultData", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "jobType", source = "jobType", defaultValue = "SUMMARY")
    @Mapping(target = "inputData", source = "name")
    ProcessingJob toEntity(ProcessingRequest request);
}
