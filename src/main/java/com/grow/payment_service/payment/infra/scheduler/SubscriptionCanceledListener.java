package com.grow.payment_service.payment.infra.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.PersistJobDataAfterExecution;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.grow.payment_service.payment.application.service.PaymentSchedulerService;
import com.grow.payment_service.payment.domain.event.SubscriptionCanceledEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class SubscriptionCanceledListener {

	private final PaymentSchedulerService paymentSchedulerService;

	@EventListener
	public void onSubscriptionCanceled(SubscriptionCanceledEvent event) {
		Long memberId = event.getMemberId();
		log.info("[구독취소 스케줄러] 이벤트 수신 memberId={}", memberId);
		paymentSchedulerService.removeBillingKeysForMember(memberId);
	}
}