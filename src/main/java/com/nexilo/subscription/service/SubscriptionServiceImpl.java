package com.nexilo.subscription.service;

import com.nexilo.common.exception.BusinessException;
import com.nexilo.common.exception.ResourceNotFoundException;
import com.nexilo.subscription.dto.SubscriptionMapper;
import com.nexilo.subscription.dto.SubscriptionRequest;
import com.nexilo.subscription.dto.SubscriptionResponse;
import com.nexilo.subscription.entity.Subscription;
import com.nexilo.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionMapper subscriptionMapper;

    @Override
    @Transactional
    public SubscriptionResponse createSubscription(SubscriptionRequest request) {
        // Check if user already has an active subscription
        boolean hasActive = subscriptionRepository.findByUserIdAndStatus(request.getUserId(), Subscription.SubscriptionStatus.ACTIVE).isPresent();
        if (hasActive) {
            throw new BusinessException("User already has an active subscription");
        }

        Subscription subscription = subscriptionMapper.toEntity(request);
        // Set end date to 30 days from now by default
        subscription.setEndDate(LocalDateTime.now().plusDays(30));
        
        Subscription savedSubscription = subscriptionRepository.save(subscription);
        return subscriptionMapper.toResponse(savedSubscription);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(Long id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + id));
        return subscriptionMapper.toResponse(subscription);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getUserSubscriptions(Long userId) {
        return subscriptionRepository.findByUserId(userId).stream()
                .map(subscriptionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SubscriptionResponse cancelSubscription(Long id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found with id: " + id));

        subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        Subscription savedSubscription = subscriptionRepository.save(subscription);
        return subscriptionMapper.toResponse(savedSubscription);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getAllSubscriptions() {
        return subscriptionRepository.findAll().stream()
                .map(subscriptionMapper::toResponse)
                .collect(Collectors.toList());
    }
}

