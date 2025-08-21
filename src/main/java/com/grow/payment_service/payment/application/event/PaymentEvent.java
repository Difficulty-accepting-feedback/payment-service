package com.grow.payment_service.payment.application.event;

import java.time.LocalDateTime;

/**
 * 결제 관련 알림을 위한 이벤트 객체
 */
public record PaymentEvent(
	Long memberId,
	String type,
	String code,
	String title,
	String content,
	String orderId,
	Integer amount,
	LocalDateTime occurredAt
) {
}