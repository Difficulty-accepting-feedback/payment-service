package com.grow.payment_service.payment.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.infra.paymentprovider.TossException;
import com.grow.payment_service.payment.infra.paymentprovider.TossInitResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossPaymentClient;
import com.grow.payment_service.payment.infra.paymentprovider.TossPaymentResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

	private final TossPaymentClient tossClient;
	private final PaymentRepository paymentRepository;
	private final PaymentHistoryRepository historyRepository;

	@Transactional
	public TossInitResponse createPayment(
		Long memberId,
		Long planId,
		Long orderId,
		int amount
	) {
		// 토스에 결제 요청
		TossInitResponse init = tossClient.initPayment(
			String.valueOf(orderId),
			amount,
			"GROW #" + orderId,
			"http://localhost:8080/api/payments/confirm",
			"http://localhost:8080/api/payments/confirm?fail"
		);

		// 받은 paymentKey 로 도메인 객체 생성
		Payment payment = Payment.create(
			memberId,
			planId,
			orderId,
			init.getPaymentKey(),    //
			null,                    // billingKey
			"cust_" + memberId,      // customerKey
			(long) amount,
			"CARD"                   // method
		);
		payment = paymentRepository.save(payment);

		// PaymentHistory 저장
		historyRepository.save(
			PaymentHistory.create(
				payment.getPaymentId(),
				payment.getPayStatus(),
				"결제 생성"
			)
		);

		return init;
	}

	@Transactional
	public Payment confirmPayment(
		String paymentKey,
		String orderIdStr,
		int amount,
		Long memberId
	) {
		// 토스 승인 API 호출
		TossPaymentResponse resp = tossClient.confirmPayment(paymentKey, orderIdStr, amount);

		Long orderId = Long.parseLong(orderIdStr);

		// orderId 로 조회
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new TossException("orderId에 해당하는 결제 내역이 없습니다." + orderId));

		// 상태 변경
		if ("DONE".equals(resp.getStatus())) {
			payment = payment.markDone();
		} else {
			payment = payment.markFailed(null);
		}
		payment = paymentRepository.save(payment);

		// 히스토리 저장
		historyRepository.save(
			PaymentHistory.create(payment.getPaymentId(), payment.getPayStatus(), "결제 " + resp.getStatus())
		);

		return payment;
	}

	@Transactional(readOnly = true)
	public Long getMemberIdByOrderId(Long orderId) {
		return paymentRepository.findByOrderId(orderId)
			.map(p -> p.getMemberId())
			.orElseThrow(() ->
				new TossException("orderId에 해당하는 결제 내역이 없습니다: " + orderId)
			);
	}

}