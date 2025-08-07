package com.grow.payment_service.plan.domain.model.enums;

import lombok.Getter;

@Getter
public enum PlanPeriod {
	MONTHLY(1),
	QUARTERLY(3),
	YEARLY(12);

	private final int months;

	PlanPeriod(int months) {
		this.months = months;
	}
}