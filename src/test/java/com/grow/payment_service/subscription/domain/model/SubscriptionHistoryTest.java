package com.grow.payment_service.subscription.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;

class SubscriptionHistoryTest {

	private static final Long MEMBER_ID = 42L;

	@Test
	@DisplayName("createRenewal(MONTHLY): ACTIVE 상태, now ~ now+1개월, changeAt=null")
	void createRenewal_monthly() {
		// given
		Clock fixed = Clock.fixed(Instant.parse("2025-08-09T00:00:00Z"), ZoneId.of("UTC"));
		LocalDateTime now = LocalDateTime.now(fixed);

		// when
		SubscriptionHistory h = SubscriptionHistory.createRenewal(MEMBER_ID, PlanPeriod.MONTHLY, fixed);

		// then
		assertNull(h.getSubscriptionHistoryId());
		assertEquals(MEMBER_ID, h.getMemberId());
		assertEquals(SubscriptionStatus.ACTIVE, h.getSubscriptionStatus());
		assertEquals(PlanPeriod.MONTHLY, h.getPeriod());
		assertEquals(now, h.getStartAt());
		assertEquals(now.plusMonths(1), h.getEndAt());
		assertNull(h.getChangeAt());
	}

	@Test
	@DisplayName("createRenewal(YEARLY): ACTIVE 상태, now ~ now+1년, changeAt=null")
	void createRenewal_yearly() {
		// given
		Clock fixed = Clock.fixed(Instant.parse("2024-12-31T12:34:56Z"), ZoneId.of("UTC"));
		LocalDateTime now = LocalDateTime.now(fixed);

		// when
		SubscriptionHistory h = SubscriptionHistory.createRenewal(MEMBER_ID, PlanPeriod.YEARLY, fixed);

		// then
		assertEquals(SubscriptionStatus.ACTIVE, h.getSubscriptionStatus());
		assertEquals(PlanPeriod.YEARLY, h.getPeriod());
		assertEquals(now, h.getStartAt());
		assertEquals(now.plusYears(1), h.getEndAt());
		assertNull(h.getChangeAt());
	}

	@Test
	@DisplayName("createExpiry: EXPIRED 상태로 주어진 기간/시각을 그대로 반영")
	void createExpiry_setsFields() {
		// given
		LocalDateTime s = LocalDateTime.of(2025, 1, 1, 0, 0);
		LocalDateTime e = LocalDateTime.of(2025, 2, 1, 0, 0);
		LocalDateTime c = LocalDateTime.of(2025, 2, 1, 0, 1);

		// when
		SubscriptionHistory h = SubscriptionHistory.createExpiry(
			MEMBER_ID, PlanPeriod.MONTHLY, s, e, c
		);

		// then
		assertNull(h.getSubscriptionHistoryId());
		assertEquals(MEMBER_ID, h.getMemberId());
		assertEquals(SubscriptionStatus.EXPIRED, h.getSubscriptionStatus());
		assertEquals(PlanPeriod.MONTHLY, h.getPeriod());
		assertEquals(s, h.getStartAt());
		assertEquals(e, h.getEndAt());
		assertEquals(c, h.getChangeAt());
	}

	@Test
	@DisplayName("of: 모든 필드가 그대로 설정된다")
	void of_setsAllFields() {
		// given
		Long id = 999L;
		LocalDateTime s = LocalDateTime.of(2025, 3, 10, 9, 0);
		LocalDateTime e = LocalDateTime.of(2025, 4, 10, 9, 0);
		LocalDateTime c = LocalDateTime.of(2025, 4, 10, 9, 1);

		// when
		SubscriptionHistory h = SubscriptionHistory.of(
			id, MEMBER_ID, SubscriptionStatus.EXPIRED, PlanPeriod.MONTHLY, s, e, c
		);

		// then
		assertEquals(id, h.getSubscriptionHistoryId());
		assertEquals(MEMBER_ID, h.getMemberId());
		assertEquals(SubscriptionStatus.EXPIRED, h.getSubscriptionStatus());
		assertEquals(PlanPeriod.MONTHLY, h.getPeriod());
		assertEquals(s, h.getStartAt());
		assertEquals(e, h.getEndAt());
		assertEquals(c, h.getChangeAt());
	}
}