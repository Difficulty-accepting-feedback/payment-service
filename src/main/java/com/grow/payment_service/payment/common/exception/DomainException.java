package com.grow.payment_service.payment.common.exception;


public class DomainException extends RuntimeException {
	public DomainException(String message) {
		super(message);
	}
}