package com.grow.payment_service.payment.infra.batch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.quartz.*;

import com.grow.payment_service.payment.application.service.PaymentBatchService;

@DisplayName("AutoChargeJobListener 테스트")
class AutoChargeJobListenerTest {

	@Mock Scheduler scheduler;
	@Mock PaymentBatchService batchService;
	@InjectMocks AutoChargeJobListener listener;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	/**
	 * Helper: retryCount, maxRetry를 담은 JobExecutionContext 생성
	 */
	private JobExecutionContext makeContext(int retryCount, int maxRetry) {
		JobDetail jobDetail = JobBuilder.newJob(DummyJob.class)
			.withIdentity("jobKey")
			.usingJobData("retryCount", retryCount)
			.usingJobData("maxRetry", maxRetry)
			.build();

		JobExecutionContext ctx = mock(JobExecutionContext.class);
		when(ctx.getJobDetail()).thenReturn(jobDetail);
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

		// -- WHEN
		listener.jobWasExecuted(ctx, /* jobEx= */ null);

		// -- THEN
		JobDataMap data = ctx.getJobDetail().getJobDataMap();
		assertEquals(0, data.getInt("retryCount"), "성공 시 retryCount는 0으로 초기화");

		verify(scheduler, times(1)).deleteJob(key);
		verify(scheduler, never()).scheduleJob(any(Trigger.class));
		verify(batchService, never()).markAutoChargeFailedPermanently();
	}

	@Test
	@DisplayName("재시도 조건(retryCount < maxRetry)에서 scheduleJob 호출")
	void testJobWasExecuted_retry() throws Exception {
		JobExecutionException jobEx = new JobExecutionException("error");
		JobExecutionContext ctx = makeContext(1, 3);
		JobKey key = ctx.getJobDetail().getKey();

		// -- WHEN
		listener.jobWasExecuted(ctx, jobEx);

		// -- THEN
		JobDataMap data = ctx.getJobDetail().getJobDataMap();
		assertEquals(2, data.getInt("retryCount"), "retryCount가 1→2로 증가");

		// 지수 백오프 + Jitter로 scheduleJob 호출
		verify(scheduler, times(1)).scheduleJob(any(Trigger.class));
		verify(batchService, never()).markAutoChargeFailedPermanently();
		verify(scheduler, never()).deleteJob(key);
	}

	@Test
	@DisplayName("최대 재시도 초과 시 markAutoChargeFailedPermanently + deleteJob 호출")
	void testJobWasExecuted_finalFailure() throws Exception {
		JobExecutionException jobEx = new JobExecutionException("fatal");
		JobExecutionContext ctx = makeContext(3, 3);
		JobKey key = ctx.getJobDetail().getKey();

		// -- WHEN
		listener.jobWasExecuted(ctx, jobEx);

		// -- THEN
		JobDataMap data = ctx.getJobDetail().getJobDataMap();
		assertEquals(4, data.getInt("retryCount"), "retryCount가 3→4로 증가");

		verify(batchService, times(1)).markAutoChargeFailedPermanently();
		verify(scheduler, times(1)).deleteJob(key);
		verify(scheduler, never()).scheduleJob(any(Trigger.class));
	}
}