package com.nexilo.subscription.controller;

import com.nexilo.subscription.dto.SubscriptionRequest;
import com.nexilo.subscription.dto.SubscriptionResponse;
import com.nexilo.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "Subscription management APIs")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @Operation(summary = "Create a new subscription")
    public ResponseEntity<SubscriptionResponse> createSubscription(@Valid @RequestBody SubscriptionRequest request) {
        return new ResponseEntity<>(subscriptionService.createSubscription(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subscription by ID")
    public ResponseEntity<SubscriptionResponse> getSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getSubscription(id));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all subscriptions for a user")
    public ResponseEntity<List<SubscriptionResponse>> getUserSubscriptions(@PathVariable Long userId) {
        return ResponseEntity.ok(subscriptionService.getUserSubscriptions(userId));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a subscription")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.cancelSubscription(id));
    }

    @GetMapping
    @Operation(summary = "Get all subscriptions (Admin)")
    public ResponseEntity<List<SubscriptionResponse>> getAllSubscriptions() {
        return ResponseEntity.ok(subscriptionService.getAllSubscriptions());
    }
}

