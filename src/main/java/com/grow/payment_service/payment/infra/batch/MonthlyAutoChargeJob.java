package com.grow.payment_service.payment.infra.batch;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import com.grow.payment_service.payment.application.service.PaymentBatchService;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
public class MonthlyAutoChargeJob implements Job {

	private final PaymentBatchService paymentBatchService;

	public MonthlyAutoChargeJob(PaymentBatchService paymentBatchService) {
		this.paymentBatchService = paymentBatchService;
	}

	/**
	 * 매월 1일 0시에 실행되는 자동 결제 작업을 수행합니다.
	 */
	@Override
	public void execute(JobExecutionContext ctx) throws JobExecutionException {
		log.info("[자동 결제] MonthlyAutoChargeJob 실행 시작");
		paymentBatchService.processMonthlyAutoCharge();
		log.info("[자동 결제] MonthlyAutoChargeJob 실행 완료");
	}
}