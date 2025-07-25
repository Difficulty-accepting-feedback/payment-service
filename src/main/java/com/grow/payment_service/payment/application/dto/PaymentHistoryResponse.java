package com.grow.payment_service.payment.application.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentHistoryResponse {
	private final String status;
	private final LocalDateTime changedAt;
	private final String reasonDetail;
}