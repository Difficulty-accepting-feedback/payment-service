package com.grow.payment_service.payment.application.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentInitResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.application.service.PaymentApplicationService;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.service.OrderIdGenerator;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.payment.saga.PaymentSagaOrchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationServiceImpl implements PaymentApplicationService {

	private final PaymentGatewayPort gatewayPort;
	private final PaymentPersistenceService persistenceService;
	private final OrderIdGenerator orderIdGenerator;
	private final PaymentRepository paymentRepository;
	private final PaymentHistoryRepository historyRepository;
	private final PaymentSagaOrchestrator paymentSaga;

	private static final String SUCCESS_URL = "http://localhost:8080/confirm"; // 임시 값
	private static final String FAIL_URL    = "http://localhost:8080/confirm?fail";  // 임시 값

	/**
	 * 주문 DB 생성 후 클라이언트에게 데이터 반환
	 */
	@Override
	@Transactional
	public PaymentInitResponse initPaymentData(
		Long memberId, Long planId, int amount
	) {
		// Redis를 이용해 고유 orderId 생성
		String orderId = orderIdGenerator.generate(memberId);

		// 도메인 객체 생성
		Payment payment = Payment.create(
			memberId, planId, orderId,
			null, null,
			"cust_" + memberId,
			(long) amount,
			"CARD"
		);
		payment = paymentRepository.save(payment);
		historyRepository.save(
			PaymentHistory.create(
				payment.getPaymentId(),
				payment.getPayStatus(),
				"주문 생성"
			)
		);

		// 클라이언트 응답
		return new PaymentInitResponse(
			orderId,
			amount,
			"GROW Plan #" + orderId,
			SUCCESS_URL + "?memberId=" + memberId + "&planId=" + planId,
			FAIL_URL    + "?memberId=" + memberId + "&planId=" + planId
		);
	}

	/**
	 * 토스 위젯이 발급한 paymentKey 로 승인 처리
	 *  (외부 API 호출 ↔ DB 저장 분리, SAGA 위임)
	 */
	@Override
	public Long confirmPayment(String paymentKey, String orderId, int amount) {
		// 외부 결제 API 호출(트랜잭션 바깥) + SAGA(리트라이+보상)
		return paymentSaga.confirmWithCompensation(paymentKey, orderId, amount);
	}

	/**
	 * 결제 취소 요청 처리
	 *  (외부 API 호출 ↔ DB 저장 분리, SAGA 위임)
	 */
	@Override
	public PaymentCancelResponse cancelPayment(
		String paymentKey,
		String orderId,
		int cancelAmount,
		CancelReason reason
	) {
		// 외부 결제 취소 API 호출(트랜잭션 바깥) + SAGA(리트라이+보상)
		return paymentSaga.cancelWithCompensation(paymentKey, orderId, cancelAmount, reason);
	}

	/**
	 * 빌링키 발급
	 *  (외부 API 호출 ↔ DB 저장 분리, SAGA 위임)
	 */
	@Override
	public PaymentIssueBillingKeyResponse issueBillingKey(PaymentIssueBillingKeyParam param) {
		// 외부 빌링키 발급 API 호출(트랜잭션 바깥) + SAGA(리트라이+보상)
		return paymentSaga.issueKeyWithCompensation(param);
	}

	/**
	 * 자동결제 승인
	 *  (외부 API 호출 ↔ DB 저장 분리, SAGA 위임)
	 */
	@Override
	public PaymentConfirmResponse chargeWithBillingKey(PaymentAutoChargeParam param) {
		// 외부 자동결제 API 호출(트랜잭션 바깥) + SAGA(리트라이+보상)
		return paymentSaga.autoChargeWithCompensation(param);
	}

	/**
	 * 테스트용 빌링키 발급 상태 전이 메서드
	 */
	@Override
	@Transactional
	public void testTransitionToReady(String orderId, String billingKey) {
		// 주문 조회
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new IllegalStateException("테스트용 주문이 없습니다: " + orderId));
		// 도메인 상태 전이 (registerBillingKey → AUTO_BILLING_READY)
		payment = payment.registerBillingKey(billingKey);
		paymentRepository.save(payment);
		// 히스토리 기록
		historyRepository.save(
			PaymentHistory.create(
				payment.getPaymentId(),
				payment.getPayStatus(),
				"테스트용 빌링키 전이"
			)
		);
	}
}