package com.grow.payment_service.payment.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTestBillingReadyRequest {
	/** 테스트할 주문 ID */
	private String orderId;
	/** 테스트용으로 사용할 임의의 billingKey */
	private String billingKey;
}