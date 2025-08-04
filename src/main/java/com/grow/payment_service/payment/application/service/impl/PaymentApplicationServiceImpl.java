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
import com.grow.payment_service.payment.global.exception.ErrorCode;
import com.grow.payment_service.payment.global.exception.PaymentApplicationException;
import com.grow.payment_service.payment.saga.PaymentSagaOrchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationServiceImpl implements PaymentApplicationService {

	private static final String SUCCESS_URL = "http://localhost:8080/confirm"; // 임시 값
	private static final String FAIL_URL = "http://localhost:8080/confirm?fail";  // 임시 값
	private final PaymentGatewayPort gatewayPort;
	private final PaymentPersistenceService persistenceService;
	private final OrderIdGenerator orderIdGenerator;
	private final PaymentRepository paymentRepository;
	private final PaymentHistoryRepository historyRepository;
	private final PaymentSagaOrchestrator paymentSaga;

	/**
	 * 주문 DB 생성 후 클라이언트에게 데이터 반환
	 */
	@Override
	@Transactional
	public PaymentInitResponse initPaymentData(
		Long memberId, Long planId, int amount
	) {
		try {
			// Redis를 이용해 고유 orderId 생성
			String orderId = orderIdGenerator.generate(memberId);

			// 도메인 객체 생성
			Payment payment = Payment.create(
				memberId, planId, orderId,
				null, null,
				"cust_" + memberId,
				(long)amount,
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
				FAIL_URL + "?memberId=" + memberId + "&planId=" + planId
			);
		} catch (Exception ex) {
			log.error("주문 생성 실패: memberId={}, planId={}, amount={}", memberId, planId, amount, ex);
			throw new PaymentApplicationException(ErrorCode.PAYMENT_INIT_ERROR, ex);
		}
	}

	/**
	 * 토스 위젯이 발급한 paymentKey 로 승인 처리
	 *  (외부 API 호출 ↔ DB 저장 분리, SAGA 위임)
	 */
	@Override
	public Long confirmPayment(String paymentKey, String orderId, int amount, String idempotencyKey) {
		try {
			return paymentSaga.confirmWithCompensation(paymentKey, orderId, amount, idempotencyKey);
		} catch (Exception ex) {
			log.error("결제 승인 실패: paymentKey={}, orderId={}, amount={}", paymentKey, orderId, amount, ex);
			throw new PaymentApplicationException(ErrorCode.PAYMENT_CONFIRM_ERROR, ex);
		}
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
		try {
			return paymentSaga.cancelWithCompensation(paymentKey, orderId, cancelAmount, reason);
		} catch (Exception ex) {
			log.error("결제 취소 실패: paymentKey={}, orderId={}, cancelAmount={}",
				paymentKey, orderId, cancelAmount, ex);
			throw new PaymentApplicationException(ErrorCode.PAYMENT_CANCEL_ERROR, ex);
		}
	}

	/**
	 * 빌링키 발급
	 *  (외부 API 호출 ↔ DB 저장 분리, SAGA 위임)
	 */
	@Override
	public PaymentIssueBillingKeyResponse issueBillingKey(PaymentIssueBillingKeyParam param) {
		try {
			return paymentSaga.issueKeyWithCompensation(param);
		} catch (Exception ex) {
			log.error("빌링키 발급 실패: orderId={}", param.getOrderId(), ex);
			throw new PaymentApplicationException(ErrorCode.BILLING_ISSUE_ERROR, ex);
		}
	}

	/**
	 * 자동결제 승인
	 *  (외부 API 호출 ↔ DB 저장 분리, SAGA 위임)
	 */
	@Override
	public PaymentConfirmResponse chargeWithBillingKey(PaymentAutoChargeParam param, String idempotencyKey) {
		try {
			return paymentSaga.autoChargeWithCompensation(param, idempotencyKey);
		} catch (Exception ex) {
			log.error("자동결제 승인 실패: billingKey={}, orderId={}",
				param.getBillingKey(), param.getOrderId(), ex);
			throw new PaymentApplicationException(ErrorCode.AUTO_CHARGE_ERROR, ex);
		}
	}

	/**
	 * 테스트용 빌링키 발급 상태 전이 메서드
	 */
	@Override
	@Transactional
	public void testTransitionToReady(String orderId, String billingKey) {
		// 주문 조회
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new PaymentApplicationException(ErrorCode.ORDER_NOT_FOUND));

		try {
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
		} catch (Exception ex) {
			log.error("테스트용 빌링키 전이 실패: orderId={}, billingKey={}", orderId, billingKey, ex);
			throw new PaymentApplicationException(ErrorCode.TEST_READY_ERROR, ex);
		}
	}
}