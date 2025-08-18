package com.grow.payment_service.payment.application.service;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentInitResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;

public interface PaymentApplicationService {

	/** 주문 DB 생성 후 클라이언트에게 데이터 반환 */
	PaymentInitResponse initPaymentData(Long memberId, Long planId, int amount);

	/** 토스 위젯이 발급한 paymentKey 로 승인 처리(외부 API 호출 + 퍼시스턴스 분리) */
	Long confirmPayment(
		Long memberId,
		String paymentKey,
		String orderId,
		int amount,
		String idempotencyKey
	);

	/** 결제 취소 요청 처리(외부 API 호출 + 퍼시스턴스 분리) */
	PaymentCancelResponse cancelPayment(
		Long memberId,
		String orderId,
		int cancelAmount,
		CancelReason cancelReason
	);

	/** 빌링키 발급(외부 API 호출 + 퍼시스턴스 분리) */
	PaymentIssueBillingKeyResponse issueBillingKey(
		Long memberId,
		PaymentIssueBillingKeyParam param
	);


	/** 자동결제 승인(외부 API 호출 + 퍼시스턴스 분리) */
	PaymentConfirmResponse chargeWithBillingKey(
		Long memberId,
		PaymentAutoChargeParam param,
		String idempotencyKey
	);

	/** 미결제 주문 만료 */
	void expireIfReady(Long memberId, String orderId);

	/** 테스트용 빌링키 발급 상태 전이 메서드 */
	void testTransitionToReady(String orderId, String billingKey);
}