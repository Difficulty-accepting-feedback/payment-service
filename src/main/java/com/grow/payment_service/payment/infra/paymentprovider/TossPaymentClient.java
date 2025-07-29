package com.grow.payment_service.payment.infra.paymentprovider;

import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingAuthResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingChargeResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossCancelResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossInitResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossPaymentResponse;

public interface TossPaymentClient {
	/** 결제 요청 */
	TossInitResponse initPayment(String orderId, int amount, String orderName, String successUrl, String failUrl);

	/** 결제 승인 */
	TossPaymentResponse confirmPayment(
		String paymentKey,
		String orderId,
		int amount
	);

	/** 결제 취소 */
	TossCancelResponse cancelPayment(
		String paymentKey,
		String cancelReason,
		int cancelAmount,
		String cancelReasonDetail
	);

	/** 빌링키 발급 요청 */
	TossBillingAuthResponse issueBillingKey(String authKey, String customerKey);

	/** 자동결제 요청 */
	TossBillingChargeResponse chargeWithBillingKey(
		String billingKey,
		String customerKey,
		int amount,
		String orderId,
		String orderName,
		String customerEmail,
		String customerName,
		Integer taxFreeAmount,
		Integer taxExemptionAmount
	);
}