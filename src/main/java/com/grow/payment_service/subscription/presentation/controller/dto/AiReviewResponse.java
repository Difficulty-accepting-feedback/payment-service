package com.grow.payment_service.subscription.presentation.controller.dto;

public record AiReviewResponse(
	Long memberId,
	boolean aiReviewAllowed
) {}