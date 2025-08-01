package com.grow.payment_service.payment.infra.batch;

import java.util.concurrent.ThreadLocalRandom;

import org.quartz.*;
import org.quartz.listeners.JobListenerSupport;
import org.springframework.stereotype.Component;

import com.grow.payment_service.payment.application.service.PaymentBatchService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AutoChargeJobListener extends JobListenerSupport {

	public static final String LISTENER_NAME = "AutoChargeListener";

	private final Scheduler scheduler;
	private final PaymentBatchService paymentBatchService;

	public AutoChargeJobListener(Scheduler scheduler,
		PaymentBatchService paymentBatchService) {
		this.scheduler = scheduler;
		this.paymentBatchService = paymentBatchService;
	}

	@Override
	public String getName() {
		return LISTENER_NAME;
	}

	/**
	 * PaymentAutoChargeJob 실행 후 호출
	 * - 성공: retryCount 초기화 + JobDetail 삭제
	 * - 실패: retryCount < maxRetry -> 지수 백오프 재시도
	 *         retryCount ≥ maxRetry -> 영구 실패 처리 + JobDetail 삭제
	 */
	@Override
	public void jobWasExecuted(JobExecutionContext ctx, JobExecutionException jobEx) {
		JobKey key = ctx.getJobDetail().getKey();
		JobDataMap data = ctx.getJobDetail().getJobDataMap();
		int retryCount = data.getInt("retryCount");
		int maxRetry   = data.getInt("maxRetry");

		if (jobEx == null) {
			// 성공 시
			data.put("retryCount", 0);
			log.info("[Listener] {} 성공, JobDetail 삭제", key);
			try {
				scheduler.deleteJob(key);
			} catch (SchedulerException e) {
				log.error("[Listener] Job 삭제 실패: {}", key, e);
			}
			return;
		}

		// 실패 시 재시도 카운트 증가
		retryCount++;
		data.put("retryCount", retryCount);
		log.warn("[자동결제] {} 실패: {}, retryCount={}/{}", key, jobEx.getMessage(), retryCount, maxRetry);

		if (retryCount < maxRetry) {
			// 지수 백오프 계산 + 동시에 몰려서 재시도 하는 현상을 방지하기 위해 Jitter 추가 (delay를 랜덤하게 조정)
			int baseDelayMin = 60;
			int maxDelayMin  = 24 * 60;
			double exp       = baseDelayMin * Math.pow(2, retryCount - 1);
			int delayMin     = (int) Math.min(maxDelayMin, exp);
			int jitter       = ThreadLocalRandom.current().nextInt(0, baseDelayMin);
			int actualDelay  = Math.max(1, delayMin - jitter);

			Trigger retryTrigger = TriggerBuilder.newTrigger()
				.forJob(key)
				.usingJobData(data)
				.startAt(DateBuilder.futureDate(actualDelay, DateBuilder.IntervalUnit.MINUTE))
				.build();

			try {
				scheduler.scheduleJob(retryTrigger);
				log.warn("[자동결제] 재시도 예약 (count={}, delay={}분)", retryCount, actualDelay);
			} catch (SchedulerException e) {
				log.error("[자동결제] 재시도 Trigger 등록 실패", e);
			}
		} else {
			// 재시도 한계 도달
			log.error("[자동결제] 재시도 한계({}) 도달, 실패 처리", maxRetry);
			try {
				paymentBatchService.markAutoChargeFailedPermanently();
			} catch (Exception e) {
				log.error("[자동결제] 영구 실패 처리 중 예외 발생", e);
			}
			try {
				scheduler.deleteJob(key);
				log.info("[Listener] {} 최종 실패, JobDetail 삭제", key);
			} catch (SchedulerException e) {
				log.error("[Listener] 최종 실패 후 Job 삭제 실패: {}", key, e);
			}
		}
	}
}