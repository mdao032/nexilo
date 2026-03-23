package com.nexilo.ai.service;

import com.nexilo.ai.dto.AiMapper;
import com.nexilo.ai.dto.AiRequestDto;
import com.nexilo.ai.dto.AiResponseDto;
import com.nexilo.ai.entity.AiRequest;
import com.nexilo.ai.repository.AiRepository;
import com.nexilo.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private final AiRepository aiRepository;
    private final AiMapper aiMapper;

    @Override
    @Transactional
    public AiResponseDto processRequest(AiRequestDto requestDto) {
        AiRequest entity = aiMapper.toEntity(requestDto);
        
        // Mock AI processing logic - In a real app, this would call an AI provider
        String mockResponse = "Processed prompt: " + requestDto.getPrompt();
        entity.setResponse(mockResponse);
        if (entity.getModel() == null) {
            entity.setModel("default-model");
        }

        AiRequest savedEntity = aiRepository.save(entity);
        return aiMapper.toResponse(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiResponseDto> getAllRequests() {
        return aiRepository.findAll().stream()
                .map(aiMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AiResponseDto getRequest(Long id) {
        AiRequest entity = aiRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AiRequest not found with id: " + id));
        return aiMapper.toResponse(entity);
    }
}

