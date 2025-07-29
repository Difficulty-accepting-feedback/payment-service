package com.grow.payment_service.payment.application.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.FailureReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingChargeResponse;
import com.grow.payment_service.payment.global.exception.TossException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentPersistenceServiceImpl implements PaymentPersistenceService {

	private final PaymentRepository paymentRepository;
	private final PaymentHistoryRepository historyRepository;

	/** 결제 승인 후 DB 저장 */
	@Override
	@Transactional
	public Long savePaymentConfirmation(String orderId) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new TossException("orderId 없음: " + orderId));
		payment = payment.transitionTo(PayStatus.DONE);
		payment = paymentRepository.save(payment);
		historyRepository.save(
			PaymentHistory.create(
				payment.getPaymentId(),
				payment.getPayStatus(),
				"결제 완료"
			)
		);
		return payment.getPaymentId();
	}

	/** 결제 취소 요청 후 DB 저장 */
	@Override
	@Transactional
	public PaymentCancelResponse requestCancel(String orderId, CancelReason reason, int amount) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new TossException("orderId 없음: " + orderId));

		// 이미 요청됐거나 완료된 경우 무시
		if (payment.getPayStatus() == PayStatus.CANCEL_REQUESTED
			|| payment.getPayStatus() == PayStatus.CANCELLED) {
			return new PaymentCancelResponse(payment.getPaymentId(), payment.getPayStatus().name());
		}

		// 취소 요청 상태로 전이
		payment = payment.requestCancel(reason);
		paymentRepository.save(payment);
		historyRepository.save(
			PaymentHistory.create(
				payment.getPaymentId(),
				payment.getPayStatus(),
				"취소 요청"
			)
		);
		return new PaymentCancelResponse(payment.getPaymentId(), payment.getPayStatus().name());
	}

	/** 결제 취소 완료 후 DB 저장 */
	@Override
	@Transactional
	public PaymentCancelResponse completeCancel(String orderId) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new TossException("orderId 없음: " + orderId));

		// 취소 요청 상태가 아니면 무시
		if (payment.getPayStatus() != PayStatus.CANCEL_REQUESTED) {
			return new PaymentCancelResponse(payment.getPaymentId(), payment.getPayStatus().name());
		}

		// 취소 완료 상태로 전이
		payment = payment.completeCancel();
		paymentRepository.save(payment);
		historyRepository.save(
			PaymentHistory.create(
				payment.getPaymentId(),
				payment.getPayStatus(),
				"취소 완료"
			)
		);

		return new PaymentCancelResponse(payment.getPaymentId(), payment.getPayStatus().name());
	}

	/** 빌링키 등록 후 DB 저장 */
	@Override
	@Transactional
	public PaymentIssueBillingKeyResponse saveBillingKeyRegistration(
		String orderId,
		String billingKey
	) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new TossException("주문 없음: " + orderId));
		payment = payment.registerBillingKey(billingKey);
		paymentRepository.save(payment);
		historyRepository.save(
			PaymentHistory.create(
				payment.getPaymentId(),
				payment.getPayStatus(),
				"자동결제 빌링키 등록"
			)
		);
		return new PaymentIssueBillingKeyResponse(billingKey);
	}

	/** 자동결제 승인 결과 DB 저장 */
	@Override
	@Transactional
	public PaymentConfirmResponse saveAutoChargeResult(
		String orderId,
		TossBillingChargeResponse tossRes
	) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new TossException("주문 없음: " + orderId));
		if ("DONE".equals(tossRes.getStatus())) {
			payment = payment.approveAutoBilling();
			historyRepository.save(
				PaymentHistory.create(
					payment.getPaymentId(),
					payment.getPayStatus(),
					"자동결제 승인 완료"
				)
			);
		} else {
			payment = payment.failAutoBilling(FailureReason.UNKNOWN);
			historyRepository.save(
				PaymentHistory.create(
					payment.getPaymentId(),
					payment.getPayStatus(),
					"자동결제 승인 실패"
				)
			);
		}
		paymentRepository.save(payment);
		return new PaymentConfirmResponse(
			payment.getPaymentId(),
			payment.getPayStatus().name()
		);
	}

	@Override
	@Transactional(readOnly = true)
	public Payment findByOrderId(String orderId) {
		return paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new TossException("orderId 없음: " + orderId));
	}

	@Override
	@Transactional
	public void saveForceCancelledPayment(Payment cancelled) {
		paymentRepository.save(cancelled);
		historyRepository.save(
			PaymentHistory.create(
				cancelled.getPaymentId(),
				cancelled.getPayStatus(),
				"보상 트랜잭션에 의한 강제 취소"
			)
		);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void saveHistory(Long paymentId, PayStatus status, String description) {
		historyRepository.save(
			PaymentHistory.create(
				paymentId,
				status,
				description
			)
		);
	}
}