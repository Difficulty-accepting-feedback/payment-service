package com.grow.payment_service.payment.domain.repository;

import java.util.List;
import java.util.Optional;

import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;

public interface PaymentRepository {
	Payment save(Payment payment);
	Optional<Payment> findById(Long id);
	Optional<Payment> findByOrderId(String orderId);
	List<Payment> findAllByMemberId(Long memberId);
	/** 빌링키 준비 완료된 건(월간 자동청구 대상) 조회 */
	List<Payment> findAllByPayStatusAndBillingKeyIsNotNull(PayStatus payStatus);
	/** PESSIMISTIC_WRITE 락 모드로 조회 */
	Optional<Payment> findByOrderIdForUpdate(String orderId);
}