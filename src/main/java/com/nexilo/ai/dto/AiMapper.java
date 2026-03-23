package com.nexilo.ai.dto;

import com.nexilo.ai.entity.AiRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AiMapper {
    AiResponseDto toResponse(AiRequest entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "response", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    AiRequest toEntity(AiRequestDto dto);
}

