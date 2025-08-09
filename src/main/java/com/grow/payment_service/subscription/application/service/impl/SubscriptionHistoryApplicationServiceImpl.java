package com.grow.payment_service.subscription.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.SubscriptionHistoryException;
import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.subscription.application.dto.SubscriptionHistoryResponse;
import com.grow.payment_service.subscription.application.dto.SubscriptionHistorySummary;
import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;
import com.grow.payment_service.subscription.domain.repository.SubscriptionHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionHistoryApplicationServiceImpl implements SubscriptionHistoryApplicationService {

	private final SubscriptionHistoryRepository repository;

	/** 해당 멤버의 모든 구독 이력 조회 */
	@Override
	@Transactional(readOnly = true)
	public List<SubscriptionHistoryResponse> getMySubscriptionHistories(Long memberId) {
		List<SubscriptionHistoryResponse> list = repository.findByMemberId(memberId).stream()
			.map(SubscriptionHistoryResponse::fromDomain)
			.collect(Collectors.toList());

		if (list.isEmpty()) {
			throw new SubscriptionHistoryException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
		}
		return list;
	}

	/** 하나의 구독 기간별로 묶어서, 최종 상태만 요약 반환 */
	@Override
	@Transactional(readOnly = true)
	public List<SubscriptionHistorySummary> getSubscriptionSummaries(Long memberId) {
		List<SubscriptionHistory> all = repository.findByMemberId(memberId);

		if (all.isEmpty()) {
			throw new SubscriptionHistoryException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
		}

		Map<String, List<SubscriptionHistory>> grouped = all.stream()
			.collect(Collectors.groupingBy(
				h -> h.getStartAt() + "_" + h.getEndAt(),
				LinkedHashMap::new,
				Collectors.toList()
			));

		return grouped.values().stream()
			.map(histories -> {
				SubscriptionHistory last = histories.stream()
					.max((h1, h2) -> {
						LocalDateTime c1 = h1.getChangeAt() != null ? h1.getChangeAt() : h1.getStartAt();
						LocalDateTime c2 = h2.getChangeAt() != null ? h2.getChangeAt() : h2.getStartAt();
						return c1.compareTo(c2);
					})
					.orElseThrow();
				return new SubscriptionHistorySummary(
					last.getMemberId(),
					last.getStartAt(),
					last.getEndAt(),
					last.getSubscriptionStatus()
				);
			})
			.collect(Collectors.toList());
	}

	/** 구독 갱신: ACTIVE 상태로 오늘부터 period 만큼 연장된 이력을 하나 추가 저장 */
	@Override
	@Transactional
	public void recordSubscriptionRenewal(Long memberId, PlanPeriod period) {
		SubscriptionHistory history = SubscriptionHistory.createRenewal(
			memberId,
			period,
			Clock.systemDefaultZone()
		);
		repository.save(history);
	}

	/** Quartz 에서 호출할 만료 처리 메서드 */
	@Override
	@Transactional
	public void recordExpiry(
		Long memberId,
		PlanPeriod period,
		LocalDateTime startAt,
		LocalDateTime endAt,
		LocalDateTime changeAt
	) {
		SubscriptionHistory expired = SubscriptionHistory.createExpiry(
			memberId,
			period,
			startAt,
			endAt,
			changeAt
		);
		repository.save(expired);
	}
}