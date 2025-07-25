package com.grow.payment_service.payment.infra.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.grow.payment_service.payment.infra.persistence.entity.PaymentJpaEntity;

public interface PaymentJpaRepository
	extends JpaRepository<PaymentJpaEntity, Long> {
	Optional<PaymentJpaEntity> findByOrderId(Long orderId);
	List<PaymentJpaEntity> findAllByMemberId(Long memberId);
}