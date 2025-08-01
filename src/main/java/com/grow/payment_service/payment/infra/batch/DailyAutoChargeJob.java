package com.grow.payment_service.payment.infra.batch;

import java.time.LocalDate;
import java.util.List;

import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.stereotype.Component;

import com.grow.payment_service.payment.application.service.PaymentBatchService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
public class DailyAutoChargeJob implements Job {

	private final PaymentRepository paymentRepository;
	private final PaymentHistoryRepository historyRepository;
	private final Scheduler scheduler;

	public DailyAutoChargeJob(
		PaymentRepository paymentRepository,
		PaymentHistoryRepository historyRepository,
		Scheduler scheduler
	) {
		this.paymentRepository   = paymentRepository;
		this.historyRepository   = historyRepository;
		this.scheduler           = scheduler;
	}

	/**
	 * 매일 0시에 실행
	 * 1. AUTO_BILLING_READY 결제 조회
	 * 2. 변경 이력 기준으로 오늘 결제일인 건만 필터
	 * 3. 개별 PaymentAutoChargeJob 스케줄링
	 */
	@Override
	public void execute(JobExecutionContext ctx) throws JobExecutionException {
		log.info("[스케줄러] DailyAutoChargeJob 시작");
		LocalDate today = LocalDate.now();

		List<Payment> dueList = paymentRepository
			.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_READY);

		for (Payment p : dueList) {
			try {
				boolean isDue = historyRepository
					.findLastByPaymentIdAndStatuses(
						p.getPaymentId(),
						List.of(PayStatus.AUTO_BILLING_READY, PayStatus.AUTO_BILLING_APPROVED)
					)
					.filter(h -> h.getChangedAt().toLocalDate().plusMonths(1).equals(today))
					.isPresent();
				if (!isDue) continue;

				String jobKey     = "autoChargeJob_" + p.getPaymentId();
				String triggerKey = "autoChargeTrig_" + p.getPaymentId();

				// 개별 자동결제 JobDetail 생성 (non-durable)
				JobDetail job = JobBuilder.newJob(PaymentAutoChargeJob.class)
					.withIdentity(jobKey, "autoChargeGroup")
					.usingJobData(PaymentAutoChargeJob.KEY_PAYMENT_ID, p.getPaymentId())
					.usingJobData("retryCount", 0)
					.usingJobData("maxRetry", 5)
					.build();

				// 즉시 실행 Trigger
				Trigger trigger = TriggerBuilder.newTrigger()
					.withIdentity(triggerKey, "autoChargeGroup")
					.forJob(job)
					.startNow()
					.build();

				// 이미 등록된 Job이 있으면 삭제 후 재등록
				if (scheduler.checkExists(job.getKey())) {
					scheduler.deleteJob(job.getKey());
				}
				scheduler.scheduleJob(job, trigger);
				log.info("[스케줄러] 개별 Job 스케줄 완료: paymentId={}", p.getPaymentId());

			} catch (SchedulerException e) {
				log.error("[스케줄러] Job 스케줄 중 오류: paymentId={}", p.getPaymentId(), e);
			} catch (Exception e) {
				log.error("[스케줄러] DailyAutoChargeJob 처리 중 예외: paymentId={}", p.getPaymentId(), e);
			}
		}

		log.info("[스케줄러] DailyAutoChargeJob 완료");
	}
}