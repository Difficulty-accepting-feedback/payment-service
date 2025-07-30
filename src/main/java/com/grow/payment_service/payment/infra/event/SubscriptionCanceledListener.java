package com.grow.payment_service.payment.infra.event;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.PersistJobDataAfterExecution;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.grow.payment_service.payment.application.service.PaymentBatchService;
import com.grow.payment_service.payment.domain.event.SubscriptionCanceledEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionCanceledListener {

	private final PaymentBatchService paymentBatchService;

	@EventListener
	public void onSubscriptionCanceled(SubscriptionCanceledEvent event) {
		Long memberId = event.getMemberId();
		log.info("[구독취소 이벤트] 이벤트 수신 memberId={}", memberId);
		paymentBatchService.removeBillingKeysForMember(memberId);
	}
}