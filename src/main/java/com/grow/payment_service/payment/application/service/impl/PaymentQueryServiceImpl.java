package com.grow.payment_service.payment.application.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.payment.application.dto.PaymentDetailResponse;
import com.grow.payment_service.payment.application.dto.PaymentHistoryResponse;
import com.grow.payment_service.payment.application.service.PaymentQueryService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.PaymentApplicationException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentQueryServiceImpl implements PaymentQueryService {

	private final PaymentRepository paymentRepository;
	private final PaymentHistoryRepository paymentHistoryRepository;

	@Override
	@Transactional(readOnly = true)
	public PaymentDetailResponse getPayment(Long paymentId) {
		// 도메인 조회
		Payment payment = paymentRepository.findById(paymentId)
			.orElseThrow(() -> new PaymentApplicationException(ErrorCode.PAYMENT_NOT_FOUND));

		// 기록 조회
		List<PaymentHistory> histories = paymentHistoryRepository.findByPaymentId(paymentId);

		// dto 변환
		List<PaymentHistoryResponse> historyResponses = histories.stream()
			.map(history -> new PaymentHistoryResponse(
				history.getStatus().name(),
				history.getChangedAt(),
				history.getReasonDetail()
			))
			.collect(Collectors.toList());

		return new PaymentDetailResponse(
			payment.getPaymentId(),
			payment.getMemberId(),
			payment.getPlanId(),
			payment.getOrderId(),
			payment.getPayStatus().name(),
			payment.getMethod(),
			payment.getTotalAmount(),
			historyResponses
		);
	}

	@Override
	@Transactional(readOnly = true)
	public List<PaymentDetailResponse> getPaymentsByMemberId(Long memberId) {
		List<Payment> payments = paymentRepository.findAllByMemberId(memberId);

		return payments.stream()
			// 결제 내역을 조회하고, 각 결제에 대한 기록을 조회하여 응답 DTO로 변환
			.map(payment -> {
				List<PaymentHistory> histories =
					paymentHistoryRepository.findByPaymentId(payment.getPaymentId());

				List<PaymentHistoryResponse> historyResponses = histories.stream()
					.map(h -> new PaymentHistoryResponse(
						h.getStatus().name(),
						h.getChangedAt(),
						h.getReasonDetail()
					))
					.collect(Collectors.toList());

				return new PaymentDetailResponse(
					payment.getPaymentId(),
					payment.getMemberId(),
					payment.getPlanId(),
					payment.getOrderId(),
					payment.getPayStatus().name(),
					payment.getMethod(),
					payment.getTotalAmount(),
					historyResponses
				);
			})
			.collect(Collectors.toList());
	}
}