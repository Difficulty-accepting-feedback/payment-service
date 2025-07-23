package com.grow.payment_service.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentVirtualAccountResponse {
	private String orderId;
	private String accountNumber;
	private String bankCode;
	private int validHours;
}