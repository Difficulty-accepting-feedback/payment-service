package com.grow.payment_service.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentConfirmResponse  {
	private final Long paymentId;
	private final String payStatus;
	private String paymentKey;
	private final String customerEmail;
	private final String customerName;
}