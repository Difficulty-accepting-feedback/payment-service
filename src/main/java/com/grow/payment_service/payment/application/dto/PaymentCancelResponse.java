package com.grow.payment_service.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentCancelResponse {
	private Long paymentId;
	private String status;
}