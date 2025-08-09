package com.grow.payment_service.plan.infra.persistence.mapper;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.grow.payment_service.plan.domain.model.Plan;
import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.plan.domain.model.enums.PlanType;
import com.grow.payment_service.plan.infra.persistence.entity.PlanJpaEntity;

class PlanMapperTest {

	@Test
	@DisplayName("toDomain: JPA 엔티티 → 도메인 매핑")
	void toDomain_mapsAllFields() {
		// given
		PlanJpaEntity e = PlanJpaEntity.builder()
			.planId(100L)
			.type(PlanType.SUBSCRIPTION)
			.amount(10000L)
			.period(PlanPeriod.MONTHLY)
			.benefits("basic benefits")
			.build();

		// when
		Plan d = PlanMapper.toDomain(e);

		// then
		assertNotNull(d);
		assertEquals(100L, d.getPlanId());
		assertEquals(PlanType.SUBSCRIPTION, d.getType());
		assertEquals(10000L, d.getAmount());
		assertEquals(PlanPeriod.MONTHLY, d.getPeriod());
		assertEquals("basic benefits", d.getBenefits());
	}

	@Test
	@DisplayName("toDomain: null 입력 → null 반환")
	void toDomain_nullReturnsNull() {
		assertNull(PlanMapper.toDomain(null));
	}

	@Test
	@DisplayName("toEntity: 도메인 → JPA 엔티티 매핑")
	void toEntity_mapsAllFields() {
		// given
		Plan d = Plan.of(
			7L,
			PlanType.ONE_TIME_PAYMENT,
			5000L,
			PlanPeriod.YEARLY,
			"pro benefits"
		);

		// when
		PlanJpaEntity e = PlanMapper.toEntity(d);

		// then
		assertNotNull(e);
		assertEquals(7L, e.getPlanId());
		assertEquals(PlanType.ONE_TIME_PAYMENT, e.getType());
		assertEquals(5000L, e.getAmount());
		assertEquals(PlanPeriod.YEARLY, e.getPeriod());
		assertEquals("pro benefits", e.getBenefits());
	}

	@Test
	@DisplayName("toEntity: null 입력 → null 반환")
	void toEntity_nullReturnsNull() {
		assertNull(PlanMapper.toEntity(null));
	}
}