package com.grow.payment_service.payment.application.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentDetailResponse {
	private final Long paymentId;
	private final Long memberId;
	private final Long planId;
	private final Long orderId;
	private final String payStatus;
	private final String method;
	private final Long totalAmount;
	private final List<PaymentHistoryResponse> history;
}