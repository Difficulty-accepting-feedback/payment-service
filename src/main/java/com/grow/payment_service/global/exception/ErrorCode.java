package com.grow.payment_service.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 토스 클라이언트
	TOSS_API_ERROR(HttpStatus.BAD_GATEWAY, "502-0", "toss.api.error"),
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "404-0", "order.not.found"),
	// 애플리케이션 전용
	PAYMENT_INIT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-0", "payment.init.failed"),
	PAYMENT_CONFIRM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-1", "payment.confirm.failed"),
	PAYMENT_CANCEL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-2", "payment.cancel.failed"),
	BILLING_ISSUE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-3", "billing.issue.failed"),
	AUTO_CHARGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-4", "auto.charge.failed"),
	TEST_READY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-5", "test.ready.failed"),
	PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "404-1", "payment.not.found"),
	BATCH_AUTO_CHARGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-6","batch.auto.charge.failed"),
	BATCH_CLEAR_BILLINGKEY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-7","batch.clear.billingkey.failed"),

	// Saga 에러
	SAGA_COMPENSATE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-8", "saga.compensate.error"),
	SAGA_COMPENSATE_COMPLETED(HttpStatus.INTERNAL_SERVER_ERROR, "500-9","saga.compensate.completed"),
	IDEMPOTENCY_IN_FLIGHT(HttpStatus.CONFLICT, "409-0", "idempotency.in.flight"),

	// 구독 내역 도메인
	SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "404-4", "subscription.not.found");
	private final HttpStatus status;
	private final String code;
	private final String messageCode;
}