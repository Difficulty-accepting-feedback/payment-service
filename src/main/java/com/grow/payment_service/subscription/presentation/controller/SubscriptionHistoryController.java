package com.grow.payment_service.subscription.presentation.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.subscription.application.dto.SubscriptionHistoryResponse;
import com.grow.payment_service.subscription.application.service.SubscriptionHistoryApplicationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions")
// @Tag(name = "Subscription", description = "구독 이력 관련 API")
public class SubscriptionHistoryController {

	private final SubscriptionHistoryApplicationService subscriptionHistoryApplicationService;

	// @Operation(summary = "내 구독 이력 조회", description = "인증된 멤버의 모든 구독 이력을 반환합니다.")
	@GetMapping("/me")
	public ResponseEntity<RsData<List<SubscriptionHistoryResponse>>> getMySubscriptions(
		@RequestHeader("X-Authorization-Id") Long memberId
	) {
		List<SubscriptionHistoryResponse> list = subscriptionHistoryApplicationService.getMySubscriptionHistories(memberId);
		return ResponseEntity.ok(new RsData<>("200", "내 구독 이력 조회 성공", list));
	}

	// @Operation(summary = "구독 갱신", description = "인증된 멤버의 구독을 1개월 연장하고 이력에 저장합니다.")
	@PostMapping("/renew")
	public ResponseEntity<RsData<Void>> renewSubscription(
		@RequestHeader("X-Authorization-Id") Long memberId,
		@RequestParam("period") PlanPeriod period
	) {
		subscriptionHistoryApplicationService.recordSubscriptionRenewal(memberId, period);
		return ResponseEntity.ok(new RsData<>("200", "구독 갱신 성공", null));
	}
}