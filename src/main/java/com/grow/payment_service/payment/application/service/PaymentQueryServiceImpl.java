package com.grow.payment_service.payment.application.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.payment.application.dto.PaymentDetailResponse;
import com.grow.payment_service.payment.application.dto.PaymentHistoryResponse;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;

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
			.orElseThrow(() -> new IllegalArgumentException(
				"결제 내역을 찾을 수 없습니다." + paymentId
			));
	
		// 기록 조회
		List<PaymentHistory> histories = paymentHistoryRepository.findByPaymentId(paymentId);

		// dto 변환
		List<PaymentHistoryResponse> historyResponses = histories.stream()
			.map(history -> new PaymentHistoryResponse(
				history.getStatus().name(),
				history.getChangedAt(),
				history.getReasonDetail()
			))
			.toList();
		
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
	
}