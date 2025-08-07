package com.grow.payment_service.subscription.infra.batch;

import java.util.concurrent.ThreadLocalRandom;

import org.quartz.DateBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.listeners.JobListenerSupport;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [SubscriptionExpiry] Quartz JobListener
 * - 성공: retryCount 초기화
 * - 실패: retryCount < maxRetry -> 지수 백오프 + Jitter 로 재시도 스케줄링
 *         retryCount ≥ maxRetry -> 재시도 중단
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryJobListener extends JobListenerSupport {

	public static final String LISTENER_NAME = "SubscriptionExpiryListener";

	@Override
	public String getName() {
		return LISTENER_NAME;
	}

	@Override
	public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
		JobKey key = context.getJobDetail().getKey();
		JobDataMap data = context.getJobDetail().getJobDataMap();
		int retryCount = data.getInt("retryCount");
		int maxRetry   = data.getInt("maxRetry");

		if (jobException == null) {
			// 성공 시 retryCount 초기화
			data.put("retryCount", 0);
			log.info("[SubscriptionExpiry] {} 실행 성공, retryCount 초기화", key);
			return;
		}

		// 실패 시
		log.warn("[SubscriptionExpiry] {} 실패: {}, retryCount={}/{}",
			key, jobException.getMessage(), retryCount, maxRetry);

		if (retryCount < maxRetry) {
			// retryCount 증가
			int newCount = retryCount + 1;
			data.put("retryCount", newCount);

			// 지수 백오프 계산 + Jitter
			int baseDelaySec = 60; // 기본 1분
			long exp        = baseDelaySec * (1L << (newCount - 1));
			int maxDelay     = (int) Math.min(exp, 3600); // 최대 1시간
			int jitter       = ThreadLocalRandom.current().nextInt(0, baseDelaySec);
			int delaySec     = Math.max(1, maxDelay - jitter);

			Trigger retryTrigger = TriggerBuilder.newTrigger()
				.forJob(key)
				.usingJobData(data)
				.startAt(DateBuilder.futureDate(delaySec, DateBuilder.IntervalUnit.SECOND))
				.build();

			try {
				context.getScheduler().scheduleJob(retryTrigger);
				log.info("[SubscriptionExpiry] 재시도 예약: delay={}초, retryCount={}/{}",
					delaySec, newCount, maxRetry);
			} catch (Exception e) {
				log.error("[SubscriptionExpiry] 재시도 Trigger 등록 실패: key={}", key, e);
			}
		} else {
			// 최대 재시도 도달, 중단
			log.error("[SubscriptionExpiry] 최대 재시도 횟수({}) 도달, 재시도 중단: {}", maxRetry, key);
		}
	}
}