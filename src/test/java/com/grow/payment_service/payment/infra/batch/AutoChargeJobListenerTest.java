package com.grow.payment_service.payment.infra.batch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.quartz.*;

import com.grow.payment_service.payment.application.service.PaymentBatchService;
import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.subscription.application.service.SubscriptionHistoryApplicationService;

@DisplayName("AutoChargeJobListener 테스트")
class AutoChargeJobListenerTest {

	@Mock Scheduler scheduler;
	@Mock PaymentBatchService batchService;
	@Mock SubscriptionHistoryApplicationService subscriptionService;
	@InjectMocks AutoChargeJobListener listener;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	/**
	 * Helper: retryCount, maxRetry, 그리고 Scheduler를 담은 JobExecutionContext 생성
	 */
	private JobExecutionContext makeContext(int retryCount, int maxRetry) {
		JobDetail jobDetail = JobBuilder.newJob(DummyJob.class)
			.withIdentity("jobKey")
			.usingJobData("retryCount", retryCount)
			.usingJobData("maxRetry", maxRetry)
			.build();

		JobExecutionContext ctx = mock(JobExecutionContext.class);
		when(ctx.getJobDetail()).thenReturn(jobDetail);
		when(ctx.getScheduler()).thenReturn(scheduler);
		return ctx;
	}

	// DummyJob은 실제 실행 내용이 없는 더미 클래스
	public static class DummyJob implements Job {
		@Override
		public void execute(JobExecutionContext context) { /* no-op */ }
	}

	@Test
	@DisplayName("성공 시 retryCount 초기화하고 deleteJob 호출")
	void testJobWasExecuted_success() throws Exception {
		JobExecutionContext ctx = makeContext(5, 3);
		JobKey key = ctx.getJobDetail().getKey();

		listener.jobWasExecuted(ctx, /* jobEx= */ null);

		JobDataMap data = ctx.getJobDetail().getJobDataMap();
		assertEquals(0, data.getInt("retryCount"), "성공 시 retryCount는 0으로 초기화");

		verify(scheduler).deleteJob(key);
		verify(scheduler, never()).scheduleJob(any(Trigger.class));
		verify(batchService, never()).markAutoChargeFailedPermanently();
		verify(subscriptionService, never()).recordExpiry(anyLong(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("재시도 조건(retryCount < maxRetry)에서 scheduleJob 호출")
	void testJobWasExecuted_retry() throws Exception {
		JobExecutionException jobEx = new JobExecutionException("error");
		JobExecutionContext ctx = makeContext(1, 3);
		JobKey key = ctx.getJobDetail().getKey();

		listener.jobWasExecuted(ctx, jobEx);

		JobDataMap data = ctx.getJobDetail().getJobDataMap();
		assertEquals(2, data.getInt("retryCount"), "retryCount가 1→2로 증가");

		verify(scheduler).scheduleJob(any(Trigger.class));
		verify(batchService, never()).markAutoChargeFailedPermanently();
		verify(scheduler, never()).deleteJob(key);
		verify(subscriptionService, never()).recordExpiry(anyLong(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("최대 재시도 초과 시 markAutoChargeFailedPermanently, recordExpiry, deleteJob 호출")
	void testJobWasExecuted_finalFailure() throws Exception {
		JobExecutionException jobEx = new JobExecutionException("fatal");
		JobExecutionContext ctx = makeContext(3, 3);
		// final failure 시 호출되는 memberId를 미리 세팅
		ctx.getJobDetail().getJobDataMap().put("memberId", 42L);

		JobKey key = ctx.getJobDetail().getKey();
		listener.jobWasExecuted(ctx, jobEx);

		JobDataMap data = ctx.getJobDetail().getJobDataMap();
		assertEquals(4, data.getInt("retryCount"), "retryCount가 3→4로 증가");

		verify(batchService).markAutoChargeFailedPermanently();
		verify(subscriptionService).recordExpiry(
			eq(42L),
			eq(PlanPeriod.MONTHLY),
			any(LocalDateTime.class),
			any(LocalDateTime.class),
			any(LocalDateTime.class)
		);
		verify(scheduler).deleteJob(key);
		verify(scheduler, never()).scheduleJob(any(Trigger.class));
	}
}