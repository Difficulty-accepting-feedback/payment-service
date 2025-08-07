package com.grow.payment_service.subscription.application.dto;

import java.time.LocalDateTime;

import com.grow.payment_service.subscription.domain.model.SubscriptionStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SubscriptionHistorySummary {
	private final Long               memberId;
	private final LocalDateTime startAt;
	private final LocalDateTime      endAt;
	private final SubscriptionStatus status;
}