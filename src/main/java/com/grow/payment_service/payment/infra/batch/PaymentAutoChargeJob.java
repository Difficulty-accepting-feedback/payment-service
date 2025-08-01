package com.grow.payment_service.payment.infra.batch;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import com.grow.payment_service.payment.application.service.PaymentBatchService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PaymentAutoChargeJob implements Job {

	public static final String KEY_PAYMENT_ID = "paymentId";
	private final PaymentBatchService paymentBatchService;

	public PaymentAutoChargeJob(PaymentBatchService paymentBatchService) {
		this.paymentBatchService = paymentBatchService;
	}

	/**
	 * 개별 결제 자동결제 실행
	 * - paymentId 하나만 처리
	 */
	@Override
	public void execute(JobExecutionContext ctx) throws JobExecutionException {
		long paymentId = ctx.getJobDetail()
			.getJobDataMap()
			.getLong(KEY_PAYMENT_ID);
		log.info("[자동결제 Job 시작] paymentId={}", paymentId);
		paymentBatchService.processSingleAutoCharge(paymentId);
		log.info("[자동결제 Job 완료] paymentId={}", paymentId);
	}
}