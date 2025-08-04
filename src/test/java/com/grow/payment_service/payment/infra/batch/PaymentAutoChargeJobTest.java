package com.grow.payment_service.payment.infra.batch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.quartz.*;

import com.grow.payment_service.payment.application.service.PaymentBatchService;

@DisplayName("PaymentAutoChargeJob 테스트 (수정판)")
class PaymentAutoChargeJobTest {

	@Mock
	private PaymentBatchService batchService;

	private PaymentAutoChargeJob job;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		job = new PaymentAutoChargeJob(batchService);
	}

	private JobExecutionContext makeContext(long paymentId) {
		JobDetail detail = JobBuilder.newJob(PaymentAutoChargeJob.class)
			.withIdentity("testJob")
			.usingJobData(PaymentAutoChargeJob.KEY_PAYMENT_ID, paymentId)
			.build();
		JobExecutionContext ctx = mock(JobExecutionContext.class);
		when(ctx.getJobDetail()).thenReturn(detail);
		return ctx;
	}

	@Test
	@DisplayName("execute() 호출 시 processSingleAutoCharge가 paymentId로 호출된다")
	void execute_callsProcessSingleAutoCharge() throws Exception {
		long paymentId = 42L;
		JobExecutionContext ctx = makeContext(paymentId);

		job.execute(ctx);

		verify(batchService, times(1)).processSingleAutoCharge(paymentId);
	}

	@Test
	@DisplayName("processSingleAutoCharge가 RuntimeException을 던지면 그대로 전파된다")
	void execute_whenServiceThrowsRuntimeException_propagates() throws Exception {
		long paymentId = 7L;
		JobExecutionContext ctx = makeContext(paymentId);

		RuntimeException rte = new IllegalStateException("runtime error");
		doThrow(rte).when(batchService).processSingleAutoCharge(paymentId);

		RuntimeException thrown = assertThrows(
			RuntimeException.class,
			() -> job.execute(ctx),
			"런타임 예외는 그대로 전파되어야 한다"
		);
		assertSame(rte, thrown);
	}
}