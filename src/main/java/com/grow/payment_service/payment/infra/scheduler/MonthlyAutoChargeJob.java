package com.grow.payment_service.payment.infra.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.PersistJobDataAfterExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.grow.payment_service.payment.application.service.PaymentSchedulerService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class MonthlyAutoChargeJob implements Job {

	@Autowired
	private PaymentSchedulerService paymentSchedulerService;

	@Override
	public void execute(JobExecutionContext context) {
		log.info("[자동결제 스케줄러] 작업 시작");
		try {
			paymentSchedulerService.processMonthlyAutoCharge();
			log.info("[자동결제 스케줄러] 작업 완료");
		} catch (Exception ex) {
			log.error("[자동결제 스케줄러] 작업 중 예외 발생", ex);
		}
	}
}