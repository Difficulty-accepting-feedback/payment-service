package com.grow.payment_service.subscription.infra.persistence.mapper;

import org.springframework.stereotype.Component;

import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionHistoryJpaEntity;

@Component
public class SubscriptionHistoryMapper {

	// 엔티티 → 도메인
	public SubscriptionHistory toDomain(SubscriptionHistoryJpaEntity e) {
		return SubscriptionHistory.of(
			e.getSubscriptionHistoryId(),
			e.getMemberId(),
			e.getSubscriptionStatus(),
			e.getPeriod(),
			e.getStartAt(),
			e.getEndAt(),
			e.getChangeAt()
		);
	}

	// 도메인 → 엔티티
	public SubscriptionHistoryJpaEntity toEntity(SubscriptionHistory d) {
		return SubscriptionHistoryJpaEntity.builder()
			.memberId(d.getMemberId())
			.subscriptionStatus(d.getSubscriptionStatus())
			.period(d.getPeriod())
			.startAt(d.getStartAt())
			.endAt(d.getEndAt())
			.changeAt(d.getChangeAt())
			.build();
	}
}