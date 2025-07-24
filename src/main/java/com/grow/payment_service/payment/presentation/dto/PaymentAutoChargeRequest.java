package com.grow.payment_service.payment.presentation.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class PaymentAutoChargeRequest {
	private final String billingKey;
	private final String customerKey;
	private final int    amount;
	private final String orderId;
	private final String orderName;
	private final String customerEmail;
	private final String customerName;
	private final Integer taxFreeAmount;
	private final Integer taxExemptionAmount;
}