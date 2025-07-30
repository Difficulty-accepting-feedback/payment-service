package com.grow.payment_service.payment.application.service;

public interface PaymentBatchService {
	/** 매월 자동결제 처리 */
	void processMonthlyAutoCharge();

	/** 구독 취소 시 해당 멤버의 빌링키 제거 */
	void removeBillingKeysForMember(Long memberId);

	/** 자동결제 실패 시 재시도 */
	void markAutoChargeFailedPermanently();
}