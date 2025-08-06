package com.grow.payment_service.subscription.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;

@DisplayName("SubscriptionHistory 도메인 모델 테스트")
class SubscriptionHistoryTest {

	private static final Long MEMBER_ID = 555L;

	@Test
	@DisplayName("createRenewal: 고정 클록으로 startAt, endAt, status, period 설정")
	void createRenewal_withFixedClock() {
		// given
		Instant instant = Instant.parse("2025-07-17T08:00:00Z");
		ZoneId zone = ZoneId.of("Asia/Seoul");
		Clock fixedClock = Clock.fixed(instant, zone);

		// when
		SubscriptionHistory sh = SubscriptionHistory.createRenewal(
			MEMBER_ID,
			PlanPeriod.MONTHLY,
			fixedClock
		);

		// then
		LocalDateTime expectedStart = LocalDateTime.ofInstant(instant, zone);
		LocalDateTime expectedEnd   = expectedStart.plusMonths(1);

		assertThat(sh.getSubscriptionHistoryId()).isNull();
		assertThat(sh.getMemberId()).isEqualTo(MEMBER_ID);
		assertThat(sh.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(sh.getPeriod()).isEqualTo(PlanPeriod.MONTHLY);
		assertThat(sh.getStartAt()).isEqualTo(expectedStart);
		assertThat(sh.getEndAt()).isEqualTo(expectedEnd);
		assertThat(sh.getChangeAt()).isNull();
	}

	@Test
	@DisplayName("of: 모든 필드가 그대로 설정되어야 한다")
	void of_setsAllFieldsDirectly() {
		// given
		Long   historyId = 888L;
		Long   memberId  = MEMBER_ID;
		SubscriptionStatus status   = SubscriptionStatus.CANCELED;
		PlanPeriod         period   = PlanPeriod.YEARLY;
		LocalDateTime      startAt  = LocalDateTime.of(2025, 6, 1, 0, 0);
		LocalDateTime      endAt    = LocalDateTime.of(2026, 6, 1, 0, 0);
		LocalDateTime      changeAt = LocalDateTime.of(2025, 6, 15, 12, 0);

		// when
		SubscriptionHistory sh = SubscriptionHistory.of(
			historyId,
			memberId,
			status,
			period,
			startAt,
			endAt,
			changeAt
		);

		// then
		assertThat(sh.getSubscriptionHistoryId()).isEqualTo(historyId);
		assertThat(sh.getMemberId()).isEqualTo(memberId);
		assertThat(sh.getSubscriptionStatus()).isEqualTo(status);
		assertThat(sh.getPeriod()).isEqualTo(period);
		assertThat(sh.getStartAt()).isEqualTo(startAt);
		assertThat(sh.getEndAt()).isEqualTo(endAt);
		assertThat(sh.getChangeAt()).isEqualTo(changeAt);
	}
}