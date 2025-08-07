package com.grow.payment_service.exception;


public class DomainException extends RuntimeException {
	public DomainException(String message) {
		super(message);
	}
}