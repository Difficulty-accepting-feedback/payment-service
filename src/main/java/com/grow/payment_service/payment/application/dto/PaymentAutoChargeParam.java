package com.grow.payment_service.payment.application.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentAutoChargeParam {
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