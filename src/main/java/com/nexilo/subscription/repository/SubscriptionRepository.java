package com.nexilo.subscription.repository;

import com.nexilo.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserId(Long userId);
    Optional<Subscription> findByUserIdAndStatus(Long userId, Subscription.SubscriptionStatus status);
}

