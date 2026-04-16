package com.nexilo.ai.service;
import com.nexilo.ai.dto.AiRequestDto;
import com.nexilo.ai.dto.AiResponseDto;
import java.util.List;
public interface AiHistoryService {
    AiResponseDto save(AiRequestDto requestDto);
    List<AiResponseDto> findAll();
    AiResponseDto findById(Long id);
}
