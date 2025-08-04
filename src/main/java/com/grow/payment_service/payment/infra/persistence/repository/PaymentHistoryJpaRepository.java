package com.grow.payment_service.payment.infra.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.infra.persistence.entity.PaymentHistoryJpaEntity;

public interface PaymentHistoryJpaRepository
	extends JpaRepository<PaymentHistoryJpaEntity, Long> {
	List<PaymentHistoryJpaEntity> findAllByPaymentId(Long paymentId);
	Optional<PaymentHistoryJpaEntity> findTop1ByPaymentIdAndStatusInOrderByChangedAtDesc(
		Long paymentId,
		List<PayStatus> statuses
	);
}