package com.grow.payment_service.payment.application.service;

import java.util.List;

import com.grow.payment_service.payment.application.dto.PaymentDetailResponse;

public interface PaymentQueryService {
	PaymentDetailResponse getPayment(Long paymentId);
	List<PaymentDetailResponse> getPaymentsByMemberId(Long memberId);
}