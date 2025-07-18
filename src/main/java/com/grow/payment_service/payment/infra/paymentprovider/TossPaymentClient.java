package com.grow.payment_service.payment.infra.paymentprovider;

public interface TossPaymentClient {
	// 결제 요청
	TossInitResponse initPayment(String orderId, int amount, String orderName, String successUrl, String failUrl);

	// 결제 승인
	TossPaymentResponse confirmPayment(String paymentKey, String orderId, int amount);
}