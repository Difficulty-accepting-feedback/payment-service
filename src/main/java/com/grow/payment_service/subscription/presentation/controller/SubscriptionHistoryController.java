package com.grow.payment_service.subscription.presentation.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.subscription.application.dto.SubscriptionHistoryResponse;
import com.grow.payment_service.subscription.application.dto.SubscriptionHistorySummary;
import com.grow.payment_service.subscription.application.service.SubscriptionHistoryApplicationService;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions")
@Tag(name = "Subscription", description = "구독 이력 API")
public class SubscriptionHistoryController {

	private final SubscriptionHistoryApplicationService subscriptionHistoryApplicationService;

	@Operation(summary = "내 구독 이력 조회", description = "요청자 회원의 모든 구독 이력을 반환합니다.")
	@GetMapping("/me")
	public ResponseEntity<RsData<List<SubscriptionHistoryResponse>>> getMySubscriptions(
		@Parameter(description = "요청자 회원 ID")
		@RequestHeader("X-Authorization-Id") Long memberId
	) {
		List<SubscriptionHistoryResponse> list =
			subscriptionHistoryApplicationService.getMySubscriptionHistories(memberId);
		return ResponseEntity.ok(new RsData<>("200", "내 구독 이력 조회 성공", list));
	}

	@Operation(summary = "구독 요약 조회", description = "기간별로 묶어 최종 상태만 요약하여 반환합니다.")
	@GetMapping("/summaries")
	public ResponseEntity<RsData<List<SubscriptionHistorySummary>>> getSummaries(
		@Parameter(description = "요청자 회원 ID")
		@RequestHeader("X-Authorization-Id") Long memberId
	) {
		List<SubscriptionHistorySummary> summaries =
			subscriptionHistoryApplicationService.getSubscriptionSummaries(memberId);
		return ResponseEntity.ok(new RsData<>("200", "구독 요약 조회 성공", summaries));
	}

	@Operation(summary = "테스트용 구독 갱신", description = "해당 회원의 구독을 period 만큼 연장(ACTIVE) 이력으로 저장합니다.")
	@PostMapping("/renew")
	public ResponseEntity<RsData<Void>> renewSubscription(
		@Parameter(description = "요청자 회원 ID")
		@RequestHeader("X-Authorization-Id") Long memberId,
		@Parameter(description = "갱신 기간 (MONTHLY/YEARLY)")
		@RequestParam("period") PlanPeriod period
	) {
		subscriptionHistoryApplicationService.recordSubscriptionRenewal(memberId, period);
		return ResponseEntity.ok(new RsData<>("200", "구독 갱신 성공", null));
	}
}