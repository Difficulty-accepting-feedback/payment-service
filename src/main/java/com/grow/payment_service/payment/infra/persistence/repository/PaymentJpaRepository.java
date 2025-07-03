package com.grow.payment_service.payment.infra.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.grow.payment_service.payment.infra.persistence.entity.PaymentJpaEntity;

public interface PaymentJpaRepository
	extends JpaRepository<PaymentJpaEntity, Long> {
}
