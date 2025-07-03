package com.grow.payment_service.payment.infra.persistence.mapper;

import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.infra.persistence.entity.PaymentJpaEntity;

public class PaymentMapper {

	public static Payment toDomain(PaymentJpaEntity e) {
		return Payment.of(
			e.getPaymentId(),
			e.getMemberId(),
			e.getPlanId(),
			e.getOrderId(),
			e.getPaymentKey(),
			e.getBillingKey(),
			e.getCustomerKey(),
			e.getTotalAmount(),
			e.getPayStatus(),
			e.getMethod(),
			e.getFailureReason(),
			e.getCancelReason()
		);
	}

	public static PaymentJpaEntity toEntity(Payment d) {
		return PaymentJpaEntity.builder()
			.paymentId(d.getPaymentId())
			.memberId(d.getMemberId())
			.planId(d.getPlanId())
			.orderId(d.getOrderId())
			.paymentKey(d.getPaymentKey())
			.billingKey(d.getBillingKey())
			.customerKey(d.getCustomerKey())
			.totalAmount(d.getTotalAmount())
			.payStatus(d.getPayStatus())
			.method(d.getMethod())
			.failureReason(d.getFailureReason())
			.cancelReason(d.getCancelReason())
			.build();
	}
}
