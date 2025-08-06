package com.grow.payment_service.subscription.application.service;

import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.SubscriptionHistoryException;
import com.grow.payment_service.subscription.application.dto.SubscriptionHistoryResponse;
import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;
import com.grow.payment_service.subscription.domain.repository.SubscriptionHistoryRepository;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionHistoryApplicationService {

	private final SubscriptionHistoryRepository repository;

	/**
	 * 해당 멤버의 모든 구독 이력 조회
	 */
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

	/**
	 * 구독 갱신: ACTIVE 상태로 오늘부터 1개월 연장된 이력을 하나 추가 저장
	 */
	@Transactional
	public void recordSubscriptionRenewal(Long memberId) {
		// Clock.systemDefaultZone()을 넘겨서 startAt=now, endAt=now+1month
		SubscriptionHistory history = new SubscriptionHistory(memberId, Clock.systemDefaultZone());
		repository.save(history);
	}

	/**
	 * Quartz 에서 호출할 만료 처리 메서드
	 */
	@Transactional
	public void recordExpiry(
		Long memberId,
		java.time.LocalDateTime startAt,
		java.time.LocalDateTime endAt,
		java.time.LocalDateTime changeAt
	) {
		SubscriptionHistory expired = new SubscriptionHistory(
			null,
			memberId,
			SubscriptionStatus.EXPIRED,
			startAt,
			endAt,
			changeAt
		);
		repository.save(expired);
	}
}