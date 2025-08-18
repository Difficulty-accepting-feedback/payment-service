package com.grow.payment_service.payment.application.service;

import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingChargeResponse;


public interface PaymentPersistenceService {

	/** 결제 승인 후 DB 저장 */
	Long savePaymentConfirmation(String orderId, String paymentKey);

	/** 결제 취소 요청 후 DB 저장 */
	PaymentCancelResponse requestCancel(String orderId, CancelReason reason, int amount);

	/** 결제 취소 완료 후 DB 저장 */
	PaymentCancelResponse completeCancel(String orderId);

	/** 빌링키 등록 후 DB 저장 */
	PaymentIssueBillingKeyResponse saveBillingKeyRegistration(String orderId, String billingKey);

	/** 자동결제 승인 결과 DB 저장 */
	PaymentConfirmResponse saveAutoChargeResult(String orderId, TossBillingChargeResponse tossRes);

	/** 보상 트랜잭션에 의해 강제 취소된 결제를 저장 */
	void saveForceCancelledPayment(Payment cancelled);

	/** 강제 상태 변경 등을 위해 결제 엔티티 단독 조회 */
	Payment findByOrderId(String orderId);

	/** 결제 상태 변경 이력 저장 */
	void saveHistory(Long paymentId, PayStatus status, String description);
}