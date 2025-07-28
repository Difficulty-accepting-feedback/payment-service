package com.grow.payment_service.payment.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.FailureReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.infra.paymentprovider.TossBillingChargeResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossException;

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

	/** 결제 취소 후 DB 저장 */
	@Override
	@Transactional
	public PaymentCancelResponse savePaymentCancellation(
		String orderId,
		CancelReason reason,
		int canceledAmount
	) {
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new TossException("orderId 없음: " + orderId));
		payment = payment.requestCancel(reason);
		payment = paymentRepository.save(payment);
		historyRepository.save(
			PaymentHistory.create(
				payment.getPaymentId(),
				payment.getPayStatus(),
				"취소 요청"
			)
		);
		payment = payment.completeCancel();
		payment = paymentRepository.save(payment);
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