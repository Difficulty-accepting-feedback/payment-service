package com.grow.payment_service.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentIssueBillingKeyResponse {
	private final String billingKey;
}