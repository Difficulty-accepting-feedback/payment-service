package com.grow.payment_service.payment.application.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentIssueBillingKeyParam {
	private final String orderId;
	private final String authKey;
	private final String customerKey;
}