package com.grow.payment_service.payment.domain.service;

public interface OrderIdGenerator {
	String generate(Long memberId);
}