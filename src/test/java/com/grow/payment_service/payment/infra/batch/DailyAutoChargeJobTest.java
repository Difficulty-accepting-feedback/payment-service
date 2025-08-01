package com.grow.payment_service.payment.infra.batch;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.SchedulerException;

import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;

@DisplayName("DailyAutoChargeJob 테스트")
class DailyAutoChargeJobTest {

	@Mock PaymentRepository paymentRepository;
	@Mock PaymentHistoryRepository historyRepository;
	@Mock Scheduler scheduler;
	@Mock JobExecutionContext context;

	@InjectMocks
	DailyAutoChargeJob job;

	@BeforeEach
	void setup() {
		MockitoAnnotations.openMocks(this);
	}

	/**
	 * 1) 하나의 AUTO_BILLING_READY 결제가 조회되고,
	 * 2) 히스토리의 changedAt이 한 달 전이면 스케줄링이 일어난다.
	 */
	@Test
	@DisplayName("due 된 결제가 있으면 scheduler.scheduleJob 호출")
	void execute_withDuePayment_schedulesIndividualJob() throws Exception {
		// today
		LocalDate today = LocalDate.now();

		// 1) 결제 리턴 세팅
		Payment p = Mockito.mock(Payment.class);
		given(p.getPaymentId()).willReturn(123L);
		given(paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_READY))
			.willReturn(List.of(p));

		// 2) 히스토리 last가 한 달 전으로 세팅
		PaymentHistory hist = Mockito.mock(PaymentHistory.class);
		LocalDateTime oneMonthAgo = today.minusMonths(1).atStartOfDay();
		given(hist.getChangedAt()).willReturn(oneMonthAgo);
		given(historyRepository.findLastByPaymentIdAndStatuses(
			eq(123L),
			argThat(list -> list.contains(PayStatus.AUTO_BILLING_READY))))
			.willReturn(Optional.of(hist));

		// 3) JobKey 존재 여부: false
		given(scheduler.checkExists(any(JobKey.class))).willReturn(false);

		// 실행
		job.execute(context);

		// verify: deleteJob()는 불필요했으므로 호출 안 됨
		verify(scheduler, never()).deleteJob(any());
		// scheduleJob() 호출
		verify(scheduler, times(1)).scheduleJob(any(JobDetail.class), any(Trigger.class));
	}

	/**
	 * 히스토리가 없거나 due 조건(false)이면 scheduleJob이 호출되지 않아야 한다.
	 */
	@Test
	@DisplayName("due 되지 않은 결제는 스케줄링하지 않음")
	void execute_withNonDuePayment_skipsScheduling() throws Exception {
		Payment p = Mockito.mock(Payment.class);
		given(p.getPaymentId()).willReturn(111L);
		given(paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_READY))
			.willReturn(List.of(p));

		// 히스토리가 없거나 filter에서 거름
		given(historyRepository.findLastByPaymentIdAndStatuses(eq(111L), anyList()))
			.willReturn(Optional.empty());

		job.execute(context);

		verify(scheduler, never()).scheduleJob(any(), any());
		verify(scheduler, never()).deleteJob(any());
	}

	/**
	 * scheduler.scheduleJob에서 예외가 나도 잡아서 진행해야 한다.
	 */
	@Test
	@DisplayName("SchedulerException 발생해도 예외 전파 없이 처리 계속")
	void execute_whenSchedulerThrows_handlesInternallyAndContinues() throws Exception {
		Payment p = Mockito.mock(Payment.class);
		given(p.getPaymentId()).willReturn(222L);
		given(paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_READY))
			.willReturn(List.of(p));

		// due 되도록 히스토리 세팅
		PaymentHistory hist = Mockito.mock(PaymentHistory.class);
		given(hist.getChangedAt()).willReturn(LocalDate.now().minusMonths(1).atStartOfDay());
		given(historyRepository.findLastByPaymentIdAndStatuses(eq(222L), anyList()))
			.willReturn(Optional.of(hist));

		// 스케줄링 중 에러 발생
		willThrow(new SchedulerException("boom"))
			.given(scheduler).scheduleJob(any(), any());

		// 실행: 예외 없어야 함
		job.execute(context);

		// scheduleJob 호출 시도 후에도 예외는 잡혀있어야 함
		verify(scheduler).scheduleJob(any(), any());
	}
}