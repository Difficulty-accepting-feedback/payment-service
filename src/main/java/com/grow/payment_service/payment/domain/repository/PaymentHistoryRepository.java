package com.grow.payment_service.payment.domain.repository;

import java.util.List;

import com.grow.payment_service.payment.domain.model.PaymentHistory;

public interface PaymentHistoryRepository {
	PaymentHistory save(PaymentHistory paymentHistory);
	List<PaymentHistory> findByPaymentId(Long paymentId);
}
