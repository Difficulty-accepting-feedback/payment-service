package com.grow.payment_service.global.exception;



public class SubscriptionHistoryException extends ServiceException {

	public SubscriptionHistoryException(ErrorCode errorCode) {
		super(errorCode);
	}

	public SubscriptionHistoryException(ErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
}