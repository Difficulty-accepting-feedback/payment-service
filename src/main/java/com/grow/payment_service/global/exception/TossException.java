package com.grow.payment_service.global.exception;

public class TossException extends ServiceException {
	public TossException(ErrorCode errorCode) {
		super(errorCode);
	}

	public TossException(ErrorCode errorCode, Throwable cause) {
		super(errorCode);
		initCause(cause);
	}
}