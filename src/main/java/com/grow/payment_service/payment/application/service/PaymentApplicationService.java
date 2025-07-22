package com.grow.payment_service.payment.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.payment.application.dto.PaymentInitResponse;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.infra.paymentprovider.TossException;
import com.grow.payment_service.payment.infra.paymentprovider.TossPaymentClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

	private final TossPaymentClient tossClient;  // confirm() 만 사용
	private final PaymentRepository paymentRepository;
	private final PaymentHistoryRepository historyRepository;

	@Value("${toss.client-key}")
	private String clientKey;

	private static final String SUCCESS_URL = "http://localhost:8080/confirm"; // 임시 값
	private static final String FAIL_URL    = "http://localhost:8080/confirm?fail"; // 임시 값

	/**
	 * 주문 DB 생성 후 클라이언트에게 데이터 반환
	 */
	@Transactional
	public PaymentInitResponse initPaymentData(
		Long memberId, Long planId, Long orderId, int amount
	) {
		Payment payment = Payment.create(
			memberId, planId, orderId,
			null, null,
			"cust_" + memberId,
			(long) amount,
			"CARD"
		);
		payment = paymentRepository.save(payment);
		historyRepository.save(PaymentHistory.create(
			payment.getPaymentId(), payment.getPayStatus(), "주문 생성"
		));

		return new PaymentInitResponse(
			String.valueOf(orderId),
			amount,
			"GROW Plan #" + orderId,
			SUCCESS_URL + "?memberId=" + memberId + "&planId=" + planId,
			FAIL_URL    + "?memberId=" + memberId + "&planId=" + planId
		);
	}

	/**
	 * 토스 위젯이 발급한 paymentKey 로 승인 처리
	 */
	@Transactional
	public Long confirmPayment(String paymentKey, String orderIdStr, int amount) {
		// 토스 승인 API 호출
		tossClient.confirmPayment(paymentKey, orderIdStr, amount);

		// orderId 로 조회
		Long orderId = Long.parseLong(orderIdStr);
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new TossException("orderId에 해당하는 결제 내역이 없습니다: " + orderId));

		// 상태 변경
		payment = payment.transitionTo(PayStatus.DONE);
		payment = paymentRepository.save(payment);

		// 히스토리 저장
		historyRepository.save(PaymentHistory.create(
			payment.getPaymentId(),
			payment.getPayStatus(),
			"결제 완료"
		));

		return payment.getPaymentId();
	}
}