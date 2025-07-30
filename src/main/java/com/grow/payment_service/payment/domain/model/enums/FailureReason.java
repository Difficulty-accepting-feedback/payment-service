package com.grow.payment_service.payment.domain.model.enums;

public enum FailureReason {
	INSUFFICIENT_FUNDS, EXPIRED_CARD, NETWORK_ERROR, UNKNOWN, RETRY_EXCEEDED
}