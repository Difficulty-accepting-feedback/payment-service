package com.grow.payment_service.payment.infra.paymentprovider.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossInitResponse {
	private String paymentKey;
	private String orderId;
	private String status;       // READY
	private int totalAmount;
	private Checkout checkout;

	@Getter @NoArgsConstructor
	public static class Checkout {
		private String url;      // 결제 UI URL
	}
}