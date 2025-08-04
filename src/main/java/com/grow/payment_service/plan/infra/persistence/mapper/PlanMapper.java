package com.grow.payment_service.plan.infra.persistence.mapper;

import com.grow.payment_service.plan.domain.model.Plan;
import com.grow.payment_service.plan.infra.persistence.entity.PlanJpaEntity;

public class PlanMapper {

	public static Plan toDomain(PlanJpaEntity e) {
		if (e == null) return null;
		return Plan.of(
			e.getPlanId(),
			e.getType(),    // 이미 도메인 enum
			e.getAmount(),
			e.getPeriod(),  // 이미 도메인 enum
			e.getBenefits()
		);
	}

	public static PlanJpaEntity toEntity(Plan d) {
		if (d == null) return null;
		return PlanJpaEntity.builder()
			.planId(d.getPlanId())
			.type(d.getType())
			.amount(d.getAmount())
			.period(d.getPeriod())
			.benefits(d.getBenefits())
			.build();
	}
}