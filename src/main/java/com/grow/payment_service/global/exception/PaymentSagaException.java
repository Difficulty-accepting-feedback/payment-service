package com.grow.payment_service.global.exception;

public class PaymentSagaException extends ServiceException {
	public PaymentSagaException(ErrorCode errorCode) {
		super(errorCode);
	}

	public PaymentSagaException(ErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
}