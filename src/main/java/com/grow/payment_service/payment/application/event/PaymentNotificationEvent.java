package com.grow.payment_service.payment.application.event;

import java.time.LocalDateTime;

/**
 * 결제 관련 알림을 위한 이벤트 객체
 */
public record PaymentNotificationEvent(
	Long memberId,
	String code,
	String notificationType,
	String title,
	String content,
	String orderId,     // 결제/구독 추적용
	Long amount,        // null 허용
	LocalDateTime occurredAt
) {}