package com.nexilo.ai.service;

import com.nexilo.ai.dto.AiRequestDto;
import com.nexilo.ai.dto.AiResponseDto;

import java.util.List;

public interface AiService {
    AiResponseDto processRequest(AiRequestDto requestDto);
    List<AiResponseDto> getAllRequests();
    AiResponseDto getRequest(Long id);
}

