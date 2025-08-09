package com.grow.payment_service.subscription.infra.batch;

import static org.mockito.Mockito.*;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.quartz.*;

import com.grow.payment_service.subscription.infra.batch.SubscriptionExpiryJobListener;

class SubscriptionExpiryJobListenerTest {

	@Mock
	private Scheduler scheduler;

	@InjectMocks
	private SubscriptionExpiryJobListener listener;

	private JobKey jobKey;
	private JobDataMap dataMap;
	private JobDetail jobDetail;
	private JobExecutionContext context;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		jobKey = new JobKey("subscriptionExpiryJob");
		dataMap = new JobDataMap();
		dataMap.put("retryCount", 0);
		dataMap.put("maxRetry", 2);

		jobDetail = mock(JobDetail.class);
		when(jobDetail.getKey()).thenReturn(jobKey);
		when(jobDetail.getJobDataMap()).thenReturn(dataMap);

		context = mock(JobExecutionContext.class);
		when(context.getJobDetail()).thenReturn(jobDetail);
		when(context.getScheduler()).thenReturn(scheduler);
	}

	@Test
	void jobWasExecuted_onSuccess_shouldResetRetryCount() {
		// when: 성공 (jobException == null)
		listener.jobWasExecuted(context, null);

		// then
		assert dataMap.getInt("retryCount") == 0;
		// scheduler should not be called
		verifyNoInteractions(scheduler);
	}

	@Test
	void jobWasExecuted_onFailureUnderMaxRetry_shouldScheduleRetry() throws SchedulerException {
		// given: simulate first failure
		JobExecutionException ex = new JobExecutionException("fail");
		// retryCount starts at 0, maxRetry=2

		// when
		listener.jobWasExecuted(context, ex);

		// then retryCount incremented
		assert dataMap.getInt("retryCount") == 1;
		// should schedule one retry trigger
		verify(scheduler).scheduleJob(argThat(trigger ->
			trigger.getJobKey().equals(jobKey) &&
				trigger.getStartTime().after(new Date())
		));
	}

	@Test
	void jobWasExecuted_onFailureAtMaxRetry_shouldNotScheduleRetryAgain() throws SchedulerException {
		// given: already at maxRetry
		dataMap.put("retryCount", 2);
		JobExecutionException ex = new JobExecutionException("fail again");

		// when
		listener.jobWasExecuted(context, ex);

		// then retryCount remains at maxRetry
		assert dataMap.getInt("retryCount") == 2;
		// no additional scheduling
		verifyNoInteractions(scheduler);
	}
}