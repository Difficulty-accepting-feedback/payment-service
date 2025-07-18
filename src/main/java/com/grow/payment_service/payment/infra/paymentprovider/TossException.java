package com.grow.payment_service.payment.infra.paymentprovider;

public class TossException extends RuntimeException {
	public TossException(String message) {
		super(message);
	}
}