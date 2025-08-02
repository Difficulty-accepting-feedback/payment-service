package com.grow.payment_service.payment.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 토스 클라이언트
	TOSS_API_ERROR(HttpStatus.BAD_GATEWAY, "502-TOSS_API_ERROR", "toss.api.error"),
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "404-ORDER_NOT_FOUND", "order.not.found"),
	// 애플리케이션 전용
	PAYMENT_INIT_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-PAYMENT_INIT_ERROR", "payment.init.failed"),
	PAYMENT_CONFIRM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-PAYMENT_CONFIRM_ERROR", "payment.confirm.failed"),
	PAYMENT_CANCEL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-PAYMENT_CANCEL_ERROR", "payment.cancel.failed"),
	BILLING_ISSUE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-BILLING_ISSUE_ERROR", "billing.issue.failed"),
	AUTO_CHARGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-AUTO_CHARGE_ERROR", "auto.charge.failed"),
	TEST_READY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-TEST_READY_ERROR", "test.ready.failed"),
	PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "404-PAYMENT_NOT_FOUND", "payment.not.found"),
	BATCH_AUTO_CHARGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-BATCH_AUTO_CHARGE_ERROR","batch.auto.charge.failed"),
	BATCH_CLEAR_BILLINGKEY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-BATCH_CLEAR_BILLINGKEY_ERROR","batch.clear.billingkey.failed"),

	// Saga 에러
	SAGA_COMPENSATE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500-SAGA_COMPENSATE_ERROR", "saga.compensate.error"),
	SAGA_COMPENSATE_COMPLETED(HttpStatus.INTERNAL_SERVER_ERROR, "500-SAGA_COMPENSATE_COMPLETED","saga.compensate.completed"),
	IDEMPOTENCY_IN_FLIGHT(HttpStatus.CONFLICT, "409-IDEMPOTENCY_IN_FLIGHT", "idempotency.in.flight");

	private final HttpStatus status;
	private final String code;
	private final String messageCode;
}