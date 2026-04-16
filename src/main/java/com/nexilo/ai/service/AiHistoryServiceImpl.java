package com.nexilo.ai.service;

import com.nexilo.ai.dto.AiMapper;
import com.nexilo.ai.dto.AiRequestDto;
import com.nexilo.ai.dto.AiResponseDto;
import com.nexilo.ai.entity.AiRequest;
import com.nexilo.ai.repository.AiRepository;
import com.nexilo.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation du service d'historique des requetes AI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiHistoryServiceImpl implements AiHistoryService {

    private final AiRepository aiRepository;
    private final AiMapper aiMapper;
    private final AiProviderService aiProviderService;

    /**
     * Envoie la requete au provider IA, persiste le resultat et retourne le DTO.
     */
    @Override
    @Transactional
    public AiResponseDto save(AiRequestDto requestDto) {
        String response = aiProviderService.generateResponse(requestDto.getPrompt());
        AiRequest entity = AiRequest.builder()
                .prompt(requestDto.getPrompt())
                .response(response)
                .model(requestDto.getModel())
                .build();
        AiRequest saved = aiRepository.save(entity);
        log.info("AiRequest persisted with id={}", saved.getId());
        return aiMapper.toDto(saved);
    }

    /**
     * Recupere l'ensemble des requetes AI depuis la base.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AiResponseDto> findAll() {
        return aiRepository.findAll().stream()
                .map(aiMapper::toDto)
                .toList();
    }

    /**
     * Recupere une requete AI par son identifiant.
     */
    @Override
    @Transactional(readOnly = true)
    public AiResponseDto findById(Long id) {
        AiRequest entity = aiRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AiRequest not found with id: " + id));
        return aiMapper.toDto(entity);
    }
}
