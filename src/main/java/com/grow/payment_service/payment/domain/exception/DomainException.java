package com.grow.payment_service.payment.domain.exception;


public class DomainException extends RuntimeException {
	public DomainException(String message) {
		super(message);
	}
}