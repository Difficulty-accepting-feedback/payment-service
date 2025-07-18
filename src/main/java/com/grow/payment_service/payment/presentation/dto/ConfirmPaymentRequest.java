package com.grow.payment_service.payment.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ConfirmPaymentRequest {
	private String paymentKey;
	private String orderId;
	private int amount;
}