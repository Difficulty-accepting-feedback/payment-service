package com.grow.payment_service.payment.infra.paymentprovider;

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

	/** 가상계좌 발급 요청 */
	TossVirtualAccountResponse createVirtualAccount(
		String orderId,
		int amount,
		String orderName,
		String customerName,
		String bankCode,
		int validHours
	);
}