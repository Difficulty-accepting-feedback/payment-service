package com.grow.payment_service.payment.infra.persistence.mapper;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.infra.persistence.entity.PaymentJpaEntity;

@DisplayName("PaymentMapper 단위 테스트")
class PaymentMapperTest {

	@Test
	@DisplayName("toDomain(): JPA 엔티티 → 도메인 매핑")
	void toDomain_mapsAllFields() {
		// given
		Long paymentId     = 123L;
		Long memberId      = 456L;
		Long planId        = 789L;
		String orderId     = "order-001";
		String paymentKey  = "payKey-001";
		String billingKey  = "billKey-001";
		String customerKey = "custKey-001";
		Long totalAmount   = 50_000L;
		PayStatus status   = PayStatus.DONE;
		String method      = "CARD";
		// 실패/취소 사유는 null로 테스트
		// (필요시 실제 enum 값을 넣어도 무방)

		PaymentJpaEntity entity = PaymentJpaEntity.builder()
			.paymentId(paymentId)
			.memberId(memberId)
			.planId(planId)
			.orderId(orderId)
			.paymentKey(paymentKey)
			.billingKey(billingKey)
			.customerKey(customerKey)
			.totalAmount(totalAmount)
			.payStatus(status)
			.method(method)
			.failureReason(null)
			.cancelReason(null)
			.build();

		// when
		Payment domain = PaymentMapper.toDomain(entity);

		// then
		assertAll("엔티티의 모든 필드가 도메인으로 복사되어야 한다",
			() -> assertEquals(paymentId,     domain.getPaymentId()),
			() -> assertEquals(memberId,      domain.getMemberId()),
			() -> assertEquals(planId,        domain.getPlanId()),
			() -> assertEquals(orderId,       domain.getOrderId()),
			() -> assertEquals(paymentKey,    domain.getPaymentKey()),
			() -> assertEquals(billingKey,    domain.getBillingKey()),
			() -> assertEquals(customerKey,   domain.getCustomerKey()),
			() -> assertEquals(totalAmount,   domain.getTotalAmount()),
			() -> assertEquals(status,        domain.getPayStatus()),
			() -> assertEquals(method,        domain.getMethod()),
			() -> assertNull(domain.getFailureReason(), "failureReason은 null"),
			() -> assertNull(domain.getCancelReason(),  "cancelReason은 null")
		);
	}

	@Test
	@DisplayName("toEntity(): 도메인 → JPA 엔티티 매핑")
	void toEntity_mapsAllFields() {
		// given
		Long paymentId     = 321L;
		Long memberId      = 654L;
		Long planId        = 987L;
		String orderId     = "order-002";
		String paymentKey  = "payKey-002";
		String billingKey  = "billKey-002";
		String customerKey = "custKey-002";
		Long totalAmount   = 75_000L;
		PayStatus status   = PayStatus.CANCELLED;
		String method      = "BANK_TRANSFER";

		Payment domain = Payment.of(
			paymentId,
			memberId,
			planId,
			orderId,
			paymentKey,
			billingKey,
			customerKey,
			totalAmount,
			status,
			method,
			/* failureReason */ null,
			/* cancelReason  */ null
		);

		// when
		PaymentJpaEntity entity = PaymentMapper.toEntity(domain);

		// then
		assertAll("도메인의 모든 필드가 엔티티로 복사되어야 한다",
			() -> assertEquals(paymentId,     entity.getPaymentId()),
			() -> assertEquals(memberId,      entity.getMemberId()),
			() -> assertEquals(planId,        entity.getPlanId()),
			() -> assertEquals(orderId,       entity.getOrderId()),
			() -> assertEquals(paymentKey,    entity.getPaymentKey()),
			() -> assertEquals(billingKey,    entity.getBillingKey()),
			() -> assertEquals(customerKey,   entity.getCustomerKey()),
			() -> assertEquals(totalAmount,   entity.getTotalAmount()),
			() -> assertEquals(status,        entity.getPayStatus()),
			() -> assertEquals(method,        entity.getMethod()),
			() -> assertNull(entity.getFailureReason(), "failureReason은 null"),
			() -> assertNull(entity.getCancelReason(),  "cancelReason은 null")
		);
	}
}