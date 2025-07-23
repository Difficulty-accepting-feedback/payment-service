package com.grow.payment_service.payment.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentVirtualAccountRequest {
	private String orderId;
	private int amount;
	private String orderName;
	private String customerName;
	private String bankCode;
}