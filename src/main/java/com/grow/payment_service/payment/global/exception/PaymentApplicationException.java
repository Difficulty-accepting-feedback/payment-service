package com.grow.payment_service.payment.global.exception;


import lombok.Getter;

@Getter
public class PaymentApplicationException extends ServiceException {
	public PaymentApplicationException(ErrorCode errorCode) {
		super(errorCode);
	}

	public PaymentApplicationException(ErrorCode errorCode, Throwable cause) {
		super(errorCode);
		initCause(cause);
	}
}