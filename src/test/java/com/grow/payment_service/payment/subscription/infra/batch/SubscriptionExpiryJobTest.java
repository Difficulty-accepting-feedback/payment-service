package com.grow.payment_service.payment.subscription.infra.batch;

import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.quartz.JobExecutionContext;

import com.grow.payment_service.subscription.application.service.SubscriptionHistoryApplicationService;
import com.grow.payment_service.subscription.infra.batch.SubscriptionExpiryJob;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionHistoryJpaEntity;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionStatus;
import com.grow.payment_service.subscription.infra.persistence.repository.SubscriptionHistoryJpaRepository;

class SubscriptionExpiryJobTest {

	@Mock
	private SubscriptionHistoryJpaRepository jpaRepo;

	@Mock
	private SubscriptionHistoryApplicationService historyService;

	@InjectMocks
	private SubscriptionExpiryJob job;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void execute_whenThereAreExpiredSubscriptions_shouldCallRecordExpiry() throws Exception {
		// given
		LocalDateTime now = LocalDateTime.now();
		SubscriptionHistoryJpaEntity e1 = SubscriptionHistoryJpaEntity.builder()
			.memberId(1L)
			.subscriptionStatus(SubscriptionStatus.ACTIVE)
			.startAt(now.minusMonths(2))
			.endAt(now.minusDays(1))
			.changeAt(null)
			.build();
		SubscriptionHistoryJpaEntity e2 = SubscriptionHistoryJpaEntity.builder()
			.memberId(2L)
			.subscriptionStatus(SubscriptionStatus.ACTIVE)
			.startAt(now.minusMonths(1))
			.endAt(now.minusHours(1))
			.changeAt(null)
			.build();
		when(jpaRepo.findExpiredBefore(eq(SubscriptionStatus.ACTIVE), any(LocalDateTime.class)))
			.thenReturn(List.of(e1, e2));

		// when
		job.execute(mock(JobExecutionContext.class));

		// then
		verify(historyService).recordExpiry(eq(1L), eq(e1.getStartAt()), eq(e1.getEndAt()), any(LocalDateTime.class));
		verify(historyService).recordExpiry(eq(2L), eq(e2.getStartAt()), eq(e2.getEndAt()), any(LocalDateTime.class));
		verifyNoMoreInteractions(historyService);
	}

	@Test
	void execute_whenNoExpiredSubscriptions_shouldNotCallRecordExpiry() throws Exception {
		// given
		when(jpaRepo.findExpiredBefore(any(), any())).thenReturn(List.of());

		// when
		job.execute(mock(JobExecutionContext.class));

		// then
		verifyNoInteractions(historyService);
	}
}