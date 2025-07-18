package com.grow.payment_service.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentInitResponse {
	private String orderId;
	private int amount;
	private String orderName;
	private String successUrl; // 위젯 성공 콜백
	private String failUrl; // 위젯 실패 콜백
}