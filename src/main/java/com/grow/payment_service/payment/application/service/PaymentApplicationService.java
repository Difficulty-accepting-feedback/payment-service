package com.grow.payment_service.payment.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentInitResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.FailureReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.infra.paymentprovider.TossBillingAuthResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossBillingChargeResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossCancelResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossException;
import com.grow.payment_service.payment.infra.paymentprovider.TossPaymentClient;
import com.grow.payment_service.payment.presentation.dto.PaymentAutoChargeRequest;
import com.grow.payment_service.payment.presentation.dto.PaymentIssueBillingKeyRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

	private final TossPaymentClient tossClient;  // confirm() 만 사용
	private final PaymentRepository paymentRepository;
	private final PaymentHistoryRepository historyRepository;

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

	@Transactional
	public PaymentCancelResponse cancelPayment(
		String paymentKey,
		String orderIdStr,
		int cancelAmount,
		CancelReason reason
	) {
		// Toss API로 취소 요청
		TossCancelResponse tossRes = tossClient.cancelPayment(
			paymentKey,
			reason.name(),
			cancelAmount,
			"사용자 요청 취소"
		);
		log.info("cancelPayment response: {}", tossRes);

		// DB에서 결제 조회
		Long orderId = Long.parseLong(orderIdStr);
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new TossException("orderId에 해당하는 결제 내역이 없습니다: " + orderId));

		// 도메인 취소 요청 -> 저장 -> 히스토리
		payment = payment.requestCancel(reason);
		payment = paymentRepository.save(payment);
		historyRepository.save(PaymentHistory.create(
			payment.getPaymentId(),
			payment.getPayStatus(),
			"취소 요청"
		));

		// 도메인 취소 완료 -> 저장 -> 히스토리
		payment = payment.completeCancel();
		payment = paymentRepository.save(payment);
		historyRepository.save(PaymentHistory.create(
			payment.getPaymentId(),
			payment.getPayStatus(),
			"취소 완료"
		));

		//  최종 응답 반환
		return new PaymentCancelResponse(
			payment.getPaymentId(),
			payment.getPayStatus().name()
		);
	}

	/** 빌링키 발급 */
	@Transactional
	public PaymentIssueBillingKeyResponse issueBillingKey(PaymentIssueBillingKeyParam param) {
		// 토스에 authKey, customerKey 전송
		TossBillingAuthResponse tossRes = tossClient.issueBillingKey(
			param.getAuthKey(), param.getCustomerKey()
		);

		// orderId 조회
		Payment payment = paymentRepository.findByOrderId(param.getOrderId())
			.orElseThrow(() -> new TossException("주문 없음: " + param.getOrderId()));

		// 도메인 상태 전이
		payment = payment.registerBillingKey(tossRes.getBillingKey());
		paymentRepository.save(payment);
		historyRepository.save(PaymentHistory.create(
			payment.getPaymentId(),
			payment.getPayStatus(),
			"자동결제 빌링키 등록"
		));

		return new PaymentIssueBillingKeyResponse(tossRes.getBillingKey());
	}

	/** 자동결제 승인 */
	@Transactional
	public PaymentConfirmResponse chargeWithBillingKey(PaymentAutoChargeParam param) {
		// 토스에 빌링키 결제 요청
		TossBillingChargeResponse tossRes = tossClient.chargeWithBillingKey(
			param.getBillingKey(),
			param.getCustomerKey(),
			param.getAmount(),
			param.getOrderId(),
			param.getOrderName(),
			param.getCustomerEmail(),
			param.getCustomerName(),
			param.getTaxFreeAmount(),
			param.getTaxExemptionAmount()
		);

		// orderId로 결제 내역 조회
		Payment payment = paymentRepository.findByOrderId(Long.parseLong(tossRes.getOrderId()))
			.orElseThrow(() -> new TossException("주문 없음: " + tossRes.getOrderId()));

		// 상태 전이 및 히스토리 기록
		if ("DONE".equals(tossRes.getStatus())) {
			payment = payment.approveAutoBilling();
			historyRepository.save(PaymentHistory.create(
				payment.getPaymentId(),
				payment.getPayStatus(),
				"자동결제 승인 완료"
			));
		} else {
			payment = payment.failAutoBilling(FailureReason.UNKNOWN);
			historyRepository.save(PaymentHistory.create(
				payment.getPaymentId(),
				payment.getPayStatus(),
				"자동결제 승인 실패"
			));
		}
		paymentRepository.save(payment);

		return new PaymentConfirmResponse(payment.getPaymentId(), payment.getPayStatus().name());
	}
}