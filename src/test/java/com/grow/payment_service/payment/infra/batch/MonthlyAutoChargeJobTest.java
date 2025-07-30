package com.grow.payment_service.payment.infra.batch;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.Trigger;

import com.grow.payment_service.payment.application.service.PaymentBatchService;

@DisplayName("MonthlyAutoChargeJob 테스트")
class MonthlyAutoChargeJobTest {

	@Mock
	private PaymentBatchService batchService;
	@Mock
	private Scheduler scheduler;

	@InjectMocks
	private MonthlyAutoChargeJob job;

	private JobDetail jobDetail;
	private JobExecutionContext context;

	@BeforeEach
	void setup() {
		MockitoAnnotations.openMocks(this);

		// retryCount 와 maxRetry를 Integer 로 넣어줍니다
		jobDetail = JobBuilder.newJob(MonthlyAutoChargeJob.class)
			.withIdentity("testJob")
			.storeDurably()
			.usingJobData("retryCount", Integer.valueOf(0))
			.usingJobData("maxRetry",   Integer.valueOf(5))
			.build();

		context = mock(JobExecutionContext.class);
		when(context.getJobDetail()).thenReturn(jobDetail);
		when(context.getScheduler()).thenReturn(scheduler);
	}

	@Test
	@DisplayName("성공 시 retryCount 초기화 및 스케줄 등록 없음")
	void execute_onSuccess_shouldResetRetryCountAndNotSchedule() throws Exception {
		// given: processMonthlyAutoCharge 정상 실행
		job.execute(context);

		// then
		assertThat(jobDetail.getJobDataMap().getInt("retryCount")).isZero();
		verifyNoInteractions(scheduler);
	}

	@Test
	@DisplayName("재시도 남음: 실패 시 1분 후 재시도 트리거 등록")
	void execute_onFailureBeforeMaxRetry_shouldScheduleOneMinuteLater() throws Exception {
		// given
		doThrow(new RuntimeException("fail"))
			.when(batchService).processMonthlyAutoCharge();

		// when
		job.execute(context);

		// then: retryCount 증가
		assertThat(jobDetail.getJobDataMap().getInt("retryCount")).isEqualTo(1);

		// and: 1분 뒤 Trigger 예약 확인
		ArgumentCaptor<Trigger> captor = ArgumentCaptor.forClass(Trigger.class);
		verify(scheduler).scheduleJob(captor.capture());
		Trigger t = captor.getValue();
		long diffSec = (t.getStartTime().getTime() - System.currentTimeMillis()) / 1000;
		assertThat(diffSec).isBetween(55L, 65L);
	}

	@Test
	@DisplayName("재시도 한계 도달 시: 영구 실패 처리만 호출")
	void execute_onFailureAtMaxRetry_shouldMarkFailedAndNotSchedule() throws Exception {
		// given: 이미 retryCount=4, maxRetry=5
		jobDetail.getJobDataMap().put("retryCount", 4);
		doThrow(new RuntimeException("fail"))
			.when(batchService).processMonthlyAutoCharge();

		// when
		job.execute(context);

		// then: retryCount 가 5 로 증가
		assertThat(jobDetail.getJobDataMap().getInt("retryCount")).isEqualTo(5);
		// scheduleJob 호출 없음
		verify(scheduler, never()).scheduleJob(any());
		// 영구 실패 처리만 호출
		verify(batchService).markAutoChargeFailedPermanently();
	}
}