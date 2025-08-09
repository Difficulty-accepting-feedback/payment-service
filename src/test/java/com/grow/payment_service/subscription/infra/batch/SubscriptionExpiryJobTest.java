package com.grow.payment_service.subscription.infra.batch;

import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.subscription.application.service.SubscriptionHistoryApplicationService;
import com.grow.payment_service.subscription.infra.batch.SubscriptionExpiryJob;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionHistoryJpaEntity;
import com.grow.payment_service.subscription.domain.model.SubscriptionStatus;
import com.grow.payment_service.subscription.infra.persistence.repository.SubscriptionHistoryJpaRepository;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryJobTest {

	@Mock
	private SubscriptionHistoryJpaRepository historyJpaRepository;

	@Mock
	private SubscriptionHistoryApplicationService historyService;

	@InjectMocks
	private SubscriptionExpiryJob job;

	@Mock
	private JobExecutionContext context;

	@Test
	@DisplayName("execute: 만료 대상 없음")
	void execute_noExpired() throws JobExecutionException {
		// given: ACTIVE 상태의 YEARLY 중 만료된 이력 없음
		given(historyJpaRepository.findExpiredByPeriod(
			eq(SubscriptionStatus.ACTIVE),
			any(LocalDateTime.class),
			eq(PlanPeriod.YEARLY)))
			.willReturn(Collections.emptyList());

		// when
		job.execute(context);

		// then: recordExpiry 호출 없어야 함
		then(historyService).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("execute: 연간 플랜 만료 처리")
	void execute_withExpired() throws JobExecutionException {
		// given: 두 개의 만료된 YEARLY 이력 준비
		SubscriptionHistoryJpaEntity e1 = SubscriptionHistoryJpaEntity.builder()
			.memberId(100L)
			.subscriptionStatus(SubscriptionStatus.ACTIVE)
			.period(PlanPeriod.YEARLY)
			.startAt(LocalDateTime.of(2024, 1, 1, 0, 0))
			.endAt(LocalDateTime.of(2025, 1, 1, 0, 0))
			.changeAt(LocalDateTime.of(2025, 1, 1, 0, 0))
			.build();

		SubscriptionHistoryJpaEntity e2 = SubscriptionHistoryJpaEntity.builder()
			.memberId(200L)
			.subscriptionStatus(SubscriptionStatus.ACTIVE)
			.period(PlanPeriod.YEARLY)
			.startAt(LocalDateTime.of(2023, 6, 1, 0, 0))
			.endAt(LocalDateTime.of(2024, 6, 1, 0, 0))
			.changeAt(LocalDateTime.of(2024, 6, 1, 0, 0))
			.build();

		given(historyJpaRepository.findExpiredByPeriod(
			eq(SubscriptionStatus.ACTIVE),
			any(LocalDateTime.class),
			eq(PlanPeriod.YEARLY)))
			.willReturn(List.of(e1, e2));

		// when
		job.execute(context);

		// then: 각각 recordExpiry 호출 검증 (changeAt는 any로 처리)
		then(historyService).should(times(1)).recordExpiry(
			eq(100L),
			eq(PlanPeriod.YEARLY),
			eq(e1.getStartAt()),
			eq(e1.getEndAt()),
			any(LocalDateTime.class)
		);
		then(historyService).should(times(1)).recordExpiry(
			eq(200L),
			eq(PlanPeriod.YEARLY),
			eq(e2.getStartAt()),
			eq(e2.getEndAt()),
			any(LocalDateTime.class)
		);
	}
}