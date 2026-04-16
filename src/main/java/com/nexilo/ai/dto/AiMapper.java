package com.nexilo.ai.dto;

import com.nexilo.ai.entity.AiRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiMapper {
    AiResponseDto toDto(AiRequest entity);
    AiRequest toEntity(AiRequestDto dto);
}
