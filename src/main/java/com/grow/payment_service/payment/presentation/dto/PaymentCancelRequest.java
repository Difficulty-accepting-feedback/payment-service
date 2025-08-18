package com.grow.payment_service.payment.presentation.dto;

import com.grow.payment_service.payment.domain.model.enums.CancelReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentCancelRequest {
	@NotBlank
	private String orderId;

	@Positive
	private int cancelAmount;
	private CancelReason cancelReason;
}