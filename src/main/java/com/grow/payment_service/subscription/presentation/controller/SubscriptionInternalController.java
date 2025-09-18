package com.grow.payment_service.subscription.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.subscription.application.service.impl.SubscriptionHistoryApplicationServiceImpl;
import com.grow.payment_service.subscription.presentation.controller.dto.AiReviewResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payment/internal/subscriptions")
@RequiredArgsConstructor
public class SubscriptionInternalController {

	private final SubscriptionHistoryApplicationServiceImpl subscriptionHistoryApplicationService;

	@GetMapping("/ai-review")
	public ResponseEntity<RsData<AiReviewResponse>> canUseAiReview(
		@RequestHeader("X-Authorization-Id") Long memberId
	) {
		boolean allowed = subscriptionHistoryApplicationService.canUseAiReview(memberId);
		AiReviewResponse body = new AiReviewResponse(memberId, allowed);
		return ResponseEntity.ok(new RsData<>("200", "AI 복습 권한 조회 성공", body));
	}
}