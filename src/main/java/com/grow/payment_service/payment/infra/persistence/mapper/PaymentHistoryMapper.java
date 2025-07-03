package com.grow.payment_service.payment.infra.persistence.mapper;

import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.infra.persistence.entity.PaymentHistoryJpaEntity;

public class PaymentHistoryMapper {

	public static PaymentHistory toDomain(PaymentHistoryJpaEntity e) {
		return PaymentHistory.of(
			e.getPaymentHistoryId(),
			e.getPaymentId(),
			e.getStatus(),
			e.getChangedAt(),
			e.getReasonDetail()
		);
	}

	public static PaymentHistoryJpaEntity toEntity(PaymentHistory d) {
		return PaymentHistoryJpaEntity.builder()
			.paymentHistoryId(d.getPaymentHistoryId())
			.paymentId(d.getPaymentId())
			.status(d.getStatus())
			.changedAt(d.getChangedAt())
			.reasonDetail(d.getReasonDetail())
			.build();
	}
}