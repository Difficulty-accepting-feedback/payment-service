package com.grow.payment_service.payment.infra.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.infra.persistence.mapper.PaymentHistoryMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PaymentHistoryRepositoryImpl implements PaymentHistoryRepository {

	private final PaymentHistoryJpaRepository paymentHistoryJpaRepository;

	@Override
	public PaymentHistory save(PaymentHistory history) {
		return PaymentHistoryMapper.toDomain(
			paymentHistoryJpaRepository.save(PaymentHistoryMapper.toEntity(history))
		);
	}

	@Override
	public List<PaymentHistory> findByPaymentId(Long paymentId) {
		return paymentHistoryJpaRepository.findAllByPaymentId(paymentId).stream()
			.map(PaymentHistoryMapper::toDomain)
			.toList();
	}

	@Override
	public Optional<PaymentHistory> findLastByPaymentIdAndStatuses(
		Long paymentId,
		List<PayStatus> statuses
	) {
		return paymentHistoryJpaRepository
			.findTop1ByPaymentIdAndStatusInOrderByChangedAtDesc(paymentId, statuses)
			.map(PaymentHistoryMapper::toDomain);
	}
}