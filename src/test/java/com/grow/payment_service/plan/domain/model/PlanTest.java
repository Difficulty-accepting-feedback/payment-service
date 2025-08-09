package com.grow.payment_service.plan.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.plan.domain.model.enums.PlanType;

class PlanTest {

	@Test
	@DisplayName("create: 신규 생성 시 planId는 null")
	void create_setsNullId() {
		Plan p = Plan.create(PlanType.SUBSCRIPTION, 10000L, PlanPeriod.MONTHLY, "benefits");
		assertNull(p.getPlanId());
		assertEquals(PlanType.SUBSCRIPTION, p.getType());
		assertEquals(10000L, p.getAmount());
		assertEquals(PlanPeriod.MONTHLY, p.getPeriod());
		assertEquals("benefits", p.getBenefits());
	}

	@Test
	@DisplayName("of: 팩토리 메서드는 주어진 식별자를 그대로 반영")
	void of_keepsGivenId() {
		Plan p = Plan.of(7L, PlanType.ONE_TIME_PAYMENT, 5000L, PlanPeriod.MONTHLY, "b");
		assertEquals(7L, p.getPlanId());
		assertEquals(PlanType.ONE_TIME_PAYMENT, p.getType());
		assertEquals(5000L, p.getAmount());
		assertEquals(PlanPeriod.MONTHLY, p.getPeriod());
		assertEquals("b", p.getBenefits());
	}

	@Test
	@DisplayName("isAutoRenewal: SUBSCRIPTION + MONTHLY 일 때만 true")
	void isAutoRenewal_trueOnlyForMonthlySubscription() {
		Plan monthlySub = Plan.of(1L, PlanType.SUBSCRIPTION, 10000L, PlanPeriod.MONTHLY, "b");
		assertTrue(monthlySub.isAutoRenewal());

		Plan yearlySub = Plan.of(2L, PlanType.SUBSCRIPTION, 100000L, PlanPeriod.YEARLY, "b");
		assertFalse(yearlySub.isAutoRenewal());

		Plan monthlyOneTime = Plan.of(3L, PlanType.ONE_TIME_PAYMENT, 10000L, PlanPeriod.MONTHLY, "b");
		assertFalse(monthlyOneTime.isAutoRenewal());

		Plan yearlyOneTime = Plan.of(4L, PlanType.ONE_TIME_PAYMENT, 100000L, PlanPeriod.YEARLY, "b");
		assertFalse(yearlyOneTime.isAutoRenewal());
	}
}