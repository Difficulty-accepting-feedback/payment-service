package com.grow.payment_service.payment.infra.paymentprovider;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor
public class TossPaymentResponse {
	private String paymentKey;
	private String orderId;
	private String status;  // 기본 테스트용
	private int totalAmount;
	private String method;
}