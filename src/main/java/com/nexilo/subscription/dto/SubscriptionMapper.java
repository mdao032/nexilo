package com.nexilo.subscription.dto;

import com.nexilo.subscription.entity.Subscription;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {
    SubscriptionResponse toResponse(Subscription subscription);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "startDate", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "endDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Subscription toEntity(SubscriptionRequest request);
}

