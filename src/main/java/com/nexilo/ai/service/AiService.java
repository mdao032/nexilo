package com.nexilo.ai.service;

import com.nexilo.ai.dto.AiRequestDto;
import com.nexilo.ai.dto.AiResponseDto;

import java.util.List;

public interface AiService {
    String generateResponse(String prompt);

    AiResponseDto processRequest(AiRequestDto requestDto);
    List<AiResponseDto> getAllRequests();
    AiResponseDto getRequest(Long id);
    String summarize(String text);
}

