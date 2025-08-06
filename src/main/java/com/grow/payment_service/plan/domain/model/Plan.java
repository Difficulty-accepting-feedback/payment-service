package com.grow.payment_service.plan.domain.model;

import com.grow.payment_service.plan.infra.persistence.enums.PlanPeriod;
import com.grow.payment_service.plan.infra.persistence.enums.PlanType;

import lombok.Builder;
import lombok.Getter;

/**
 * 플랜(구독) 도메인 모델
 */
@Getter
public class Plan {

	private final Long planId;
	private final PlanType type;
	private final Long amount;
	private final PlanPeriod period;
	private final String benefits;

	@Builder
	private Plan(Long planId,
		PlanType type,
		Long amount,
		PlanPeriod period,
		String benefits) {
		this.planId    = planId;
		this.type      = type;
		this.amount    = amount;
		this.period    = period;
		this.benefits  = benefits;
	}

	/**
	 * 신규 구독 생성
	 */
	public static Plan create(PlanType type,
		Long amount,
		PlanPeriod period,
		String benefits) {
		return Plan.builder()
			.planId(null)
			.type(type)
			.amount(amount)
			.period(period)
			.benefits(benefits)
			.build();
	}

	public static Plan of(Long planId,
		PlanType type,
		Long amount,
		PlanPeriod period,
		String benefits) {
		return Plan.builder()
			.planId(planId)
			.type(type)
			.amount(amount)
			.period(period)
			.benefits(benefits)
			.build();
	}
}