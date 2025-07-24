package com.grow.payment_service.payment.presentation.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class PaymentIssueBillingKeyRequest {
	private final Long orderId;
	private final String authKey;
	private final String customerKey;
}