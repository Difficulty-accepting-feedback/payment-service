package com.grow.payment_service.payment.application.event;

import static org.springframework.transaction.event.TransactionPhase.*;


import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.grow.payment_service.payment.infra.client.NotificationClient;
import com.grow.payment_service.payment.infra.client.NotificationRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationEventHandler {

	private final NotificationClient notificationClient;

	@TransactionalEventListener(phase = AFTER_COMMIT)
	public void on(PaymentEvent e) {
		Long amountLong = e.amount() == null ? null : e.amount().longValue();

		NotificationRequest req = NotificationRequest.builder()
			.notificationType("PAYMENT")
			.memberId(e.memberId())
			.title(e.title())
			.content(e.content())
			.orderId(e.orderId())
			.amount(amountLong)
			.occurredAt(e.occurredAt())
			.build();

		notificationClient.sendPaymentEvent(req);
		log.info("[알림 전송] type={}, code={}, memberId={}, orderId={}", e.type(), e.code(), e.memberId(), e.orderId());
	}
}