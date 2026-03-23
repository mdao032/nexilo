package com.nexilo.subscription.service;

import com.nexilo.subscription.dto.SubscriptionRequest;
import com.nexilo.subscription.dto.SubscriptionResponse;

import java.util.List;

public interface SubscriptionService {
    SubscriptionResponse createSubscription(SubscriptionRequest request);
    SubscriptionResponse getSubscription(Long id);
    List<SubscriptionResponse> getUserSubscriptions(Long userId);
    SubscriptionResponse cancelSubscription(Long id);
    List<SubscriptionResponse> getAllSubscriptions();
}

