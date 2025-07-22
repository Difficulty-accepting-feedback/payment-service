package com.grow.payment_service.payment.infra.paymentprovider;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossCancelResponse {
	private String status;
	private int canceledAmount;
}