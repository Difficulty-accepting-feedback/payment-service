package com.grow.payment_service.payment.infra.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.infra.persistence.entity.PaymentHistoryJpaEntity;
import com.grow.payment_service.payment.infra.persistence.entity.PaymentJpaEntity;

import jakarta.persistence.LockModeType;

public interface PaymentJpaRepository
	extends JpaRepository<PaymentJpaEntity, Long> {
	Optional<PaymentJpaEntity> findByOrderId(String orderId);
	List<PaymentJpaEntity> findAllByMemberId(Long memberId);
	List<PaymentJpaEntity> findAllByPayStatusAndBillingKeyIsNotNull(PayStatus payStatus);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM PaymentJpaEntity p WHERE p.orderId = :orderId")
	Optional<PaymentJpaEntity> findByOrderIdForUpdate(@Param("orderId") String orderId);
}