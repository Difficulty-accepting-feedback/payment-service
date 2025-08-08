package com.grow.payment_service.payment.application.service;

public interface EmailService {
	void sendPaymentSuccess(String toEmail, String toName, String orderId, Integer amount);
	void sendPaymentFailure(String toEmail, String toName, String orderId, Integer amount);
	void sendCancellation(String toEmail, String toName, String orderId, Integer amount);
}