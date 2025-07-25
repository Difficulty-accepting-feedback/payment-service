package com.grow.payment_service.payment.application.service;

import com.grow.payment_service.payment.application.dto.PaymentDetailResponse;

public interface PaymentQueryService {
	PaymentDetailResponse getPayment(Long paymentId);
}