package com.grow.payment_service.payment.infra.paymentprovider.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossCancelResponse {
	private String status;
	private int canceledAmount;
}