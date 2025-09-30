package com.grow.payment_service.payment.saga;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.global.metrics.PaymentMetrics;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.PaymentSagaException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * [GROW 결제] 보상 트랜잭션 처리 서비스
 * - DB 저장 실패 시 상태 전이 및 히스토리 기록
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationTransactionService {

	private final PaymentPersistenceService persistenceService;
	private final PaymentMetrics metrics;

	private void markComp(String type, Throwable cause) {
		metrics.result("payment_compensation_total",
			"type", type,
			"cause_class", cause == null ? "N/A" : cause.getClass().getSimpleName()
		);
	}


	/** 결제 승인 저장 실패 보상 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void compensateApprovalFailure(String orderId, Throwable cause) {
		try {
			markComp("approval", cause);
			log.warn("[결제-Comp] 승인 보상: orderId={}, cause={}", orderId, cause.toString());
			Payment payment = persistenceService.findByOrderId(orderId);
			Payment cancelled = payment.forceCancel(CancelReason.SYSTEM_ERROR);
			persistenceService.saveForceCancelledPayment(cancelled);
			persistenceService.saveHistory(
				cancelled.getPaymentId(),
				cancelled.getPayStatus(),
				"보상-승인 취소 완료"
			);
		} catch (Exception ex) {
			throw new PaymentSagaException(ErrorCode.SAGA_COMPENSATE_ERROR, ex);
		}
	}

	/** 결제 취소요청 저장 실패 보상 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public PaymentCancelResponse compensateCancelRequestFailure(String orderId, Throwable cause) {
		try {
			markComp("cancel_req", cause);
			log.warn("[결제-Comp] 취소요청 보상: orderId={}, cause={}", orderId, cause.toString());
			Payment payment = persistenceService.findByOrderId(orderId);
			Payment cancelled = payment.forceCancel(CancelReason.SYSTEM_ERROR);
			persistenceService.saveForceCancelledPayment(cancelled);
			persistenceService.saveHistory(
				cancelled.getPaymentId(),
				cancelled.getPayStatus(),
				"보상-취소요청 완료"
			);
			return new PaymentCancelResponse(
				cancelled.getPaymentId(),
				cancelled.getPayStatus().name()
			);
		} catch (Exception ex) {
			throw new PaymentSagaException(ErrorCode.SAGA_COMPENSATE_ERROR, ex);
		}
	}

	/** 결제 취소완료 DB반영 실패 - 상태를 '취소 완료'로 수정 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public PaymentCancelResponse compensateCancelCompleteFailure(String orderId, Throwable cause) {
		try {
			markComp("cancel_complete", cause);
			log.warn("[결제-Comp] 취소 완료로 수정: orderId={}, cause={}", orderId, cause.toString());
			Payment payment = persistenceService.findByOrderId(orderId);
			if (payment.getPayStatus() == PayStatus.CANCEL_REQUESTED) {
				Payment completed = payment.completeCancel();
				persistenceService.saveForceCancelledPayment(completed);
				persistenceService.saveHistory(
					completed.getPaymentId(),
					completed.getPayStatus(),
					"보상-취소완료 처리"
				);
				return new PaymentCancelResponse(
					completed.getPaymentId(),
					completed.getPayStatus().name()
				);
			}
			log.error("[결제-Comp] 취소 완료로 수정 실패: 상태 오류, orderId={}, status={}",
				orderId, payment.getPayStatus());
			throw new IllegalStateException(
				"취소 완료로 수정 실패 - 잘못된 상태: " + payment.getPayStatus()
			);
		} catch (Exception ex) {
			throw new PaymentSagaException(ErrorCode.SAGA_COMPENSATE_ERROR, ex);
		}
	}

	/** 빌링키 발급 저장 실패 (보상 없음, 예외만) */
	public void compensateIssueKeyFailure(String orderId, String billingKey, Throwable cause) {
		markComp("issue_key", cause);
		log.error("[결제-Comp] 빌링키 저장 실패: orderId={}, billingKey={}, cause={}",
			orderId, billingKey, cause.toString());
		throw new PaymentSagaException(
			ErrorCode.SAGA_COMPENSATE_ERROR,
			new IllegalStateException("빌링키 저장 재시도 실패", cause)
		);
	}

	/** 자동결제 저장 실패 보상 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void compensateAutoChargeFailure(String orderId, Throwable cause) {
		try {
			markComp("auto_charge", cause);
			log.warn("[결제-Comp] 자동결제 보상: orderId={}, cause={}", orderId, cause.toString());
			Payment payment = persistenceService.findByOrderId(orderId);
			Payment cancelled = payment.forceCancel(CancelReason.SYSTEM_ERROR);
			persistenceService.saveForceCancelledPayment(cancelled);
			persistenceService.saveHistory(
				cancelled.getPaymentId(),
				cancelled.getPayStatus(),
				"보상-자동결제 취소 완료"
			);
		} catch (Exception ex) {
			throw new PaymentSagaException(ErrorCode.SAGA_COMPENSATE_ERROR, ex);
		}
	}
}