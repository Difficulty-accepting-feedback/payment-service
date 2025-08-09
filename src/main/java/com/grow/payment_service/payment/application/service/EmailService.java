package com.grow.payment_service.payment.application.service;

public interface EmailService {

	/**
	 * 결제 성공 안내 메일 발송
	 */
	void sendPaymentSuccess(
		String toEmail,
		String toName,
		String orderId,
		Integer amount,
		String receiptUrl,
		String method,
		String easyPayProvider,
		String requestedAt,
		String approvedAt,
		String currency
	);

	/**
	 * 결제 실패 안내 메일 발송
	 */
	void sendPaymentFailure(
		String toEmail,
		String toName,
		String orderId,
		Integer amount,
		String receiptUrl,
		String method,
		String easyPayProvider,
		String requestedAt,
		String approvedAt,
		String currency
	);

	/**
	 * 결제 취소 안내 메일 발송
	 */
	void sendCancellation(
		String toEmail,
		String toName,
		String orderId,
		Integer amount,
		String receiptUrl,
		String method,
		String easyPayProvider,
		String requestedAt,
		String approvedAt,
		String cancelReason,
		Integer cancelAmount,
		String currency
	);
}