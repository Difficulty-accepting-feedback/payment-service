package com.grow.payment_service.plan.presentation.dto;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.plan.domain.model.enums.PlanType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PlanResponse {
	private Long planId;
	private PlanType type;       // SUBSCRIPTION, ONE_TIME_PAYMENT
	private PlanPeriod period;   // MONTHLY, QUARTERLY, YEARLY
	private Long amount;
	private String benefits;
}