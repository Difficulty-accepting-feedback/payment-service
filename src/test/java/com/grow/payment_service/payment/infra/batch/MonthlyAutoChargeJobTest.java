package com.grow.payment_service.payment.infra.batch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;

import com.grow.payment_service.payment.application.service.PaymentBatchService;

@DisplayName("MonthlyAutoChargeJob 테스트")
class MonthlyAutoChargeJobTest {

	@Mock
	private PaymentBatchService batchService;
	@Mock
	private Scheduler scheduler;  // MonthlyAutoChargeJob 에서는 사용되지 않음

	@InjectMocks
	private MonthlyAutoChargeJob job;

	private JobExecutionContext context;

	@BeforeEach
	void setup() {
		MockitoAnnotations.openMocks(this);
		context = mock(JobExecutionContext.class);
		when(context.getScheduler()).thenReturn(scheduler);
	}

	@Test
	@DisplayName("성공 시: processMonthlyAutoCharge() 호출")
	void execute_onSuccess_shouldInvokeProcess() throws Exception {
		// given: 정상 동작 (batchService.processMonthlyAutoCharge() 예외 없음)

		// when
		job.execute(context);

		// then
		verify(batchService, times(1)).processMonthlyAutoCharge();
		verifyNoMoreInteractions(batchService);
		// scheduler 는 전혀 사용되지 않아야 함
		verifyNoInteractions(scheduler);
	}

	@Test
	@DisplayName("실패 시: RuntimeException 예외 전파")
	void execute_onFailure_shouldPropagateException() {
		// given: processMonthlyAutoCharge() 에서 예외 발생
		doThrow(new RuntimeException("자동결제 실패")).when(batchService).processMonthlyAutoCharge();

		// when & then
		assertThatThrownBy(() -> job.execute(context))
			.isInstanceOf(RuntimeException.class)
			.hasMessage("자동결제 실패");

		verify(batchService, times(1)).processMonthlyAutoCharge();
		verifyNoInteractions(scheduler);
	}
}