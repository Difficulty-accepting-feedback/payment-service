package com.grow.payment_service.payment.domain.service;

import com.grow.payment_service.payment.infra.paymentprovider.TossBillingAuthResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossBillingChargeResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossCancelResponse;

/**
 * 결제 게이트웨이 포트 인터페이스
 * (현재 다른 결제 게이트웨이 추가 계획이 없기 때문에 인프라 dto 직접 사용)
 */
public interface PaymentGatewayPort {
	/** 결제 승인(토스) */
	void confirmPayment(String paymentKey, String orderId, int amount);

	/** 결제 취소(토스) */
	TossCancelResponse cancelPayment(String paymentKey, String reason, int amount, String message);

	/** 빌링키 발급(토스) */
	TossBillingAuthResponse issueBillingKey(String authKey, String customerKey);

	/** 빌링키로 자동결제(토스) */
	TossBillingChargeResponse chargeWithBillingKey(
		String billingKey,
		String customerKey,
		int amount,
		String orderId,
		String orderName,
		String customerEmail,
		String customerName,
		int taxFreeAmount,
		int taxExemptionAmount
	);
}