package com.grow.payment_service.payment.application.service.impl;

import org.springframework.stereotype.Service;

import com.grow.payment_service.payment.application.service.EmailService;
import com.grow.payment_service.payment.presentation.dto.WebhookRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WebhookService {

	private final EmailService emailService;

	/** 결제 완료/실패 웹훅 처리 */
	public void onPaymentStatusChanged(WebhookRequest.WebhookData d) {
		if ("DONE".equals(d.getStatus())) {
			emailService.sendPaymentSuccess(
				d.getCustomerEmail(),
				d.getCustomerName(),
				d.getOrderId(),
				d.getAmount()
			);
		} else if ("FAILED".equals(d.getStatus())) {
			emailService.sendPaymentFailure(
				d.getCustomerEmail(),
				d.getCustomerName(),
				d.getOrderId(),
				d.getAmount()
			);
		}
	}

	/** 결제 취소 완료 웹훅 처리 */
	public void onCancelStatusChanged(WebhookRequest.WebhookData d) {
		if ("CANCELLED".equals(d.getStatus())) {
			emailService.sendCancellation(
				d.getCustomerEmail(),
				d.getCustomerName(),
				d.getOrderId(),
				d.getAmount()
			);
		}
	}
}