package com.grow.payment_service.payment.infra.batch;

import org.quartz.DateBuilder;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.PersistJobDataAfterExecution;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.grow.payment_service.payment.application.service.PaymentBatchService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
@Component
public class MonthlyAutoChargeJob implements Job {

	@Autowired
	private PaymentBatchService paymentBatchService;

	@Override
	public void execute(JobExecutionContext ctx) {
		JobDataMap data = ctx.getJobDetail().getJobDataMap();
		int retryCount = data.getIntValue("retryCount");
		int maxRetry   = data.getIntValue("maxRetry");
		try {
			// 실제 자동결제 처리
			paymentBatchService.processMonthlyAutoCharge();
			// 성공 시 retryCount 초기화
			data.put("retryCount", 0);

		} catch (Exception ex) {
			retryCount++;
			data.put("retryCount", retryCount);

			if (retryCount < maxRetry) {
				// 고정 지연: 1분 후에 재시도
				Trigger retryTrigger = TriggerBuilder.newTrigger()
					.forJob(ctx.getJobDetail())
					.withIdentity("monthlyAutoChargeRetry_" + retryCount, "retry")
					.startAt(DateBuilder.futureDate(1, DateBuilder.IntervalUnit.MINUTE))
					.usingJobData(data)
					.build();
				try {
					ctx.getScheduler().scheduleJob(retryTrigger);
					log.warn("[자동결제] 재시도 예약 (count={} delay=1분)", retryCount);
				} catch (SchedulerException se) {
					log.error("[자동결제] 재시도 Trigger 등록 실패", se);
				}

			} else {
				// 최대 재시도 횟수 도달 시 영구 실패 처리
				log.error("[자동결제] 재시도 한계({}회) 도달, 실패 처리", maxRetry, ex);
				paymentBatchService.markAutoChargeFailedPermanently();
			}
			// 예외를 던지지 않고 정상 종료 처리
		}
	}
}