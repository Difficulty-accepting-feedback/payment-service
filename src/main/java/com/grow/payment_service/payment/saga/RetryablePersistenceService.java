package com.grow.payment_service.payment.saga;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.retry.annotation.Retry;

import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.infra.paymentprovider.TossBillingChargeResponse;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RetryablePersistenceService {

	private final PaymentPersistenceService persistenceService;
	private final PaymentGatewayPort gatewayPort;

	/**
	 * 1) 결제 승인 정보를 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> recoverConfirm에서 보상(자동 취소) 실행
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverConfirm")
	@Transactional
	public Long saveConfirmation(String paymentKey, String orderId, int amount) {
		return persistenceService.savePaymentConfirmation(orderId);
	}
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected Long recoverConfirm(String paymentKey, String orderId, int amount, Throwable t) {
		// 외부 결제 보상 취소 요청
		gatewayPort.cancelPayment(paymentKey, CancelReason.SYSTEM_ERROR.name(), amount, "보상 취소");

		try {
			// 내부 상태 강제 CANCELLED 처리
			Payment payment = persistenceService.findByOrderId(orderId);
			Payment cancelled = payment.forceCancel(CancelReason.SYSTEM_ERROR);
			persistenceService.saveForceCancelledPayment(cancelled);

			// 히스토리 기록
			persistenceService.saveHistory(
				cancelled.getPaymentId(),
				cancelled.getPayStatus(),
				"보상 트랜잭션으로 강제 취소됨"
			);

		} catch (Exception ex) {
			throw new IllegalStateException("보상 취소 실패", ex);
		}

		throw new IllegalStateException("결제 승인 보상(취소) 완료 - 원인: " + t.getMessage(), t);
	}

	/**
	 * 1) 사용자 요청 결제 취소 요청 내역을 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> recoverCancelRequest에서 보상 트랜잭션 실행
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverCancelRequest")
	@Transactional
	public PaymentCancelResponse saveCancelRequest(
		String paymentKey, String orderId, int amount, CancelReason reason
	) {
		return persistenceService.requestCancel(orderId, reason, amount);
	}
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected PaymentCancelResponse recoverCancelRequest(
		String paymentKey, String orderId, int amount, CancelReason reason, Throwable t
	) {
		// 보상 트랜잭션: 외부는 이미 cancel 완료 상태이므로 내부 강제 상태 전이
		Payment payment = persistenceService.findByOrderId(orderId);
		Payment cancelled = payment.forceCancel(CancelReason.SYSTEM_ERROR);
		persistenceService.saveForceCancelledPayment(cancelled);
		persistenceService.saveHistory(
			cancelled.getPaymentId(),
			cancelled.getPayStatus(),
			"보상 트랜잭션(취소 요청 저장 실패)에 의한 강제 상태 전이"
		);

		return new PaymentCancelResponse(
			cancelled.getPaymentId(),
			cancelled.getPayStatus().name()
		);
	}

	/**
	 * 1) 사용자 요청 결제 취소 완료 내역을 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> recoverCancelComplete에서 보상 트랜잭션 실행
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverCancelComplete")
	@Transactional
	public PaymentCancelResponse saveCancelComplete(String orderId) {
		return persistenceService.completeCancel(orderId);
	}
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected PaymentCancelResponse recoverCancelComplete(String orderId, Throwable t) {
		// 보상 트랜잭션: 아직 CANCEL_REQUESTED 상태라면 강제로 완료 처리
		Payment payment = persistenceService.findByOrderId(orderId);
		if (payment.getPayStatus() == PayStatus.CANCEL_REQUESTED) {
			Payment completed = payment.completeCancel();
			persistenceService.saveForceCancelledPayment(completed);
			persistenceService.saveHistory(
				completed.getPaymentId(),
				completed.getPayStatus(),
				"보상 트랜잭션(취소 완료 저장 실패)에 의한 강제 완료"
			);
			return new PaymentCancelResponse(
				completed.getPaymentId(),
				completed.getPayStatus().name()
			);
		}
		throw new IllegalStateException(
			"보상 트랜잭션: 적절한 상태가 아닙니다 - " + payment.getPayStatus(), t
		);
	}

	/**
	 * 1) 자동결제 빌링키를 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> recoverIssueKey에서 예외 전파
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverIssueKey")
	@Transactional
	public PaymentIssueBillingKeyResponse saveBillingKey(String orderId, String billingKey) {
		return persistenceService.saveBillingKeyRegistration(orderId, billingKey);
	}
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected PaymentIssueBillingKeyResponse recoverIssueKey(String orderId, String billingKey, Throwable t) {
		// 빌링키 발급 실패 시 별도 취소 로직 없음
		throw new IllegalStateException("빌링키 발급 DB저장 재시도 실패", t);
	}

	/**
	 * 1) 자동결제 결과를 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> recoverAutoCharge에서 보상(자동 취소) 실행
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverAutoCharge")
	@Transactional
	public PaymentConfirmResponse saveAutoCharge(
		String billingKey, String orderId, int amount, TossBillingChargeResponse tossRes
	) {
		return persistenceService.saveAutoChargeResult(orderId, tossRes);
	}
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	protected PaymentConfirmResponse recoverAutoCharge(
		String billingKey, String orderId, int amount, TossBillingChargeResponse tossRes, Throwable t
	) {
		// 보상 트랜잭션: 자동결제 취소 처리
		gatewayPort.cancelPayment(
			billingKey,
			CancelReason.SYSTEM_ERROR.name(),
			amount,
			"자동결제 보상 취소"
		);

		// 취소 요청
		persistenceService.requestCancel(orderId, CancelReason.SYSTEM_ERROR, amount);

		// 취소 처리
		persistenceService.completeCancel(orderId);

		throw new IllegalStateException("자동결제 승인 보상(취소) 실패", t);
	}
}