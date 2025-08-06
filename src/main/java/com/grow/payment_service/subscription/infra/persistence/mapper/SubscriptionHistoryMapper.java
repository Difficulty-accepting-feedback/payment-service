package com.grow.payment_service.subscription.infra.persistence.mapper;

import org.springframework.stereotype.Component;

import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionHistoryJpaEntity;

@Component
public class SubscriptionHistoryMapper {

	// 엔티티에서 도메인으로 변환
	public SubscriptionHistory toDomain(SubscriptionHistoryJpaEntity entity) {
		return new SubscriptionHistory(
			entity.getSubscriptionHistoryId(),
			entity.getMemberId(),
			entity.getSubscriptionStatus(),
			entity.getStartAt(),
			entity.getEndAt(),
			entity.getChangeAt()
		);
	}

	// 도메인에서 엔티티로 변환
	public SubscriptionHistoryJpaEntity toEntity(SubscriptionHistory domain) {
		return SubscriptionHistoryJpaEntity.builder()
			.memberId(domain.getMemberId())
            .subscriptionStatus(domain.getSubscriptionStatus())
			.startAt(domain.getStartAt())
			.endAt(domain.getEndAt())
			.changeAt(domain.getChangeAt())
			.build();
	}
}