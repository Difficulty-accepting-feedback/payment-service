package com.grow.payment_service.payment.infra.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.grow.payment_service.payment.infra.persistence.entity.PaymentHistoryJpaEntity;

public interface PaymentHistoryJpaRepository
	extends JpaRepository<PaymentHistoryJpaEntity, Long> {
	List<PaymentHistoryJpaEntity> findAllByPaymentId(Long paymentId);
}
