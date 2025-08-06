package com.grow.payment_service.payment.subscription.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionStatus;

class SubscriptionHistoryTest {

	private static final Long MEMBER_ID = 555L;

	@Test
	@DisplayName("Fixed Clock 공급 시 startAt, endAt, status가 정확히 설정되어야 한다")
	void constructor_withFixedClock() {
		// given
		Instant instant = Instant.parse("2025-07-17T08:00:00Z");
		ZoneId zone = ZoneId.of("Asia/Seoul");
		Clock fixedClock = Clock.fixed(instant, zone);

		// when
		SubscriptionHistory sh = new SubscriptionHistory(MEMBER_ID, fixedClock);

		// then
		LocalDateTime expectedStart = LocalDateTime.ofInstant(instant, zone);
		LocalDateTime expectedEnd   = expectedStart.plusMonths(1);

		assertThat(sh.getSubscriptionHistoryId()).isNull();
		assertThat(sh.getMemberId()).isEqualTo(MEMBER_ID);
		assertThat(sh.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(sh.getStartAt()).isEqualTo(expectedStart);
		assertThat(sh.getEndAt()).isEqualTo(expectedEnd);
		// changeAt는 첫 생성 시 설정되지 않았으므로 null
		assertThat(sh.getChangeAt()).isNull();
	}

	@Test
	@DisplayName("null Clock 공급 시 startAt/endAt가 현재 시각 범위 내에 설정되어야 한다")
	void constructor_withNullClock() {
		// given
		LocalDateTime before = LocalDateTime.now();

		// when
		SubscriptionHistory sh = new SubscriptionHistory(MEMBER_ID, (Clock) null);

		LocalDateTime afterStart = sh.getStartAt();
		LocalDateTime afterEnd   = sh.getEndAt();

		// then: startAt ∈ [before, now], endAt = startAt+1개월
		assertThat(afterStart).isBetween(before, LocalDateTime.now());
		assertThat(afterEnd).isAfterOrEqualTo(afterStart.plusMonths(1));
		assertThat(sh.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
	}

	@Test
	@DisplayName("전체 필드 생성자에서는 모든 값이 그대로 세팅되어야 한다")
	void allArgsConstructor_setsFieldsDirectly() {
		// given
		Long   historyId = 888L;
		Long   memberId  = MEMBER_ID;
		SubscriptionStatus status   = SubscriptionStatus.CANCELED;
		LocalDateTime startAt  = LocalDateTime.of(2025, 6, 1, 0, 0);
		LocalDateTime endAt    = LocalDateTime.of(2025, 7, 1, 0, 0);
		LocalDateTime changeAt = LocalDateTime.of(2025, 6, 15, 12, 0);

		// when
		SubscriptionHistory sh = new SubscriptionHistory(
			historyId, memberId, status, startAt, endAt, changeAt
		);

		// then
		assertThat(sh.getSubscriptionHistoryId()).isEqualTo(historyId);
		assertThat(sh.getMemberId()).isEqualTo(memberId);
		assertThat(sh.getSubscriptionStatus()).isEqualTo(status);
		assertThat(sh.getStartAt()).isEqualTo(startAt);
		assertThat(sh.getEndAt()).isEqualTo(endAt);
		assertThat(sh.getChangeAt()).isEqualTo(changeAt);
	}
}