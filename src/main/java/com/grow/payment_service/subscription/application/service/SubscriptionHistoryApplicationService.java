package com.grow.payment_service.subscription.application.service;

import java.time.LocalDateTime;
import java.util.List;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.subscription.application.dto.SubscriptionHistoryResponse;
import com.grow.payment_service.subscription.application.dto.SubscriptionHistorySummary;

public interface SubscriptionHistoryApplicationService {

	/** 해당 멤버의 모든 구독 이력 조회 */
	List<SubscriptionHistoryResponse> getMySubscriptionHistories(Long memberId);

	/** 하나의 구독 기간별로 묶어서, 최종 상태만 요약 반환 */
	List<SubscriptionHistorySummary> getSubscriptionSummaries(Long memberId);

	/** 구독 갱신: ACTIVE 상태로 오늘부터 period 만큼 연장된 이력을 하나 추가 저장 */
	void recordSubscriptionRenewal(Long memberId, PlanPeriod period);

	/** Quartz 에서 호출할 만료 처리 메서드 */
	void recordExpiry(
		Long memberId,
		PlanPeriod period,
		LocalDateTime startAt,
		LocalDateTime endAt,
		LocalDateTime changeAt
	);
}