package com.grow.payment_service.payment.domain.repository;

import java.util.Optional;

import com.grow.payment_service.payment.domain.model.Payment;

public interface PaymentRepository {
	Payment save(Payment payment);
	Optional<Payment> findById(Long id);
}
