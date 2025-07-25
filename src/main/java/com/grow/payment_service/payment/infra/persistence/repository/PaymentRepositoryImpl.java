package com.grow.payment_service.payment.infra.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.infra.persistence.mapper.PaymentMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

	private final PaymentJpaRepository paymentJpaRepository;

	@Override
	public Payment save(Payment payment) {
		return PaymentMapper.toDomain(
			paymentJpaRepository.save(PaymentMapper.toEntity(payment))
		);
	}

	@Override
	public Optional<Payment> findById(Long id) {
		return paymentJpaRepository.findById(id)
			.map(PaymentMapper::toDomain);
	}

	@Override
	public Optional<Payment> findByOrderId(String orderId) {
		return paymentJpaRepository.findByOrderId(orderId)
			.map(PaymentMapper::toDomain);
	}

	@Override
	public List<Payment> findAllByMemberId(Long memberId) {
		return paymentJpaRepository.findAllByMemberId(memberId)
			.stream()
			.map(PaymentMapper::toDomain)
			.toList();
	}
}