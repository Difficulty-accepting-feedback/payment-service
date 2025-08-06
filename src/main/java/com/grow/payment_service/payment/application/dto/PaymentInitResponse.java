package com.grow.payment_service.payment.application.dto;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.plan.domain.model.enums.PlanType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentInitResponse {
	private final String orderId;
	private final int amount;
	private final String orderName;
	private final String successUrl;
	private final String failUrl;
	private final Long planId;
	private final PlanType planType;
	private final PlanPeriod planPeriod;
}