package com.grow.payment_service.payment.presentation.dto;

import com.grow.payment_service.payment.domain.model.enums.CancelReason;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentCancelRequest {
	private String paymentKey;
	private String orderId;
	private int cancelAmount;
	private CancelReason cancelReason;
}