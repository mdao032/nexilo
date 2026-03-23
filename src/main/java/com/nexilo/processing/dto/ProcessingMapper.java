package com.nexilo.processing.dto;

import com.nexilo.processing.entity.ProcessingJob;
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
    ProcessingJob toEntity(ProcessingRequest request);
}

