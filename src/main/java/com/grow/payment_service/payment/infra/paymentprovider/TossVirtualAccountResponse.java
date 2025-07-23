package com.grow.payment_service.payment.infra.paymentprovider;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossVirtualAccountResponse {
	private String orderId;
	private String accountNumber;
	private String bank;
	private int validHours;
}