package com.grow.payment_service.payment.saga;

import org.springframework.stereotype.Service;
import io.github.resilience4j.retry.annotation.Retry;

import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.payment.infra.paymentprovider.TossBillingChargeResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 DB 리트라이 + 보상 트랜잭션 관리 서비스
 * 결제 승인/취소/자동결제 등 DB 저장 시 리트라이 및 보상 트랜잭션을 조율하는 역할
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetryablePersistenceService {

	private final PaymentPersistenceService persistenceService;
	private final PaymentGatewayPort gatewayPort;
	private final CompensationTransactionService compensationTxService;

	/**
	 * 1) 결제 승인 정보를 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> compensateApprovalFailure에서 보상(자동 취소) 실행
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverConfirm")
	public Long saveConfirmation(String paymentKey, String orderId, int amount) {
		log.info("[결제-Retry] 결제 승인 정보 DB 저장 시도: orderId={}", orderId);
		return persistenceService.savePaymentConfirmation(orderId);
	}

	/**
	 * 결제 승인 정보 저장 실패 시 보상(자동 취소)
	 * - 외부 결제 보상 취소 요청 후 내부 보상 트랜잭션 실행
	 */
	public Long recoverConfirm(String paymentKey, String orderId, int amount, Throwable t) {
		log.error("[결제-Retry] 결제 승인 DB 저장 실패, 보상 트랜잭션 실행: orderId={}, cause={}", orderId, t.toString());
		gatewayPort.cancelPayment(paymentKey, CancelReason.SYSTEM_ERROR.name(), amount, "보상 취소");
		compensationTxService.compensateApprovalFailure(orderId, t);
		throw new IllegalStateException("결제 승인 보상(취소) 완료 - 원인: " + t.getMessage(), t);
	}

	/**
	 * 1) 사용자 요청 결제 취소 요청 내역을 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> compensateCancelRequestFailure에서 보상 트랜잭션 실행
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverCancelRequest")
	public PaymentCancelResponse saveCancelRequest(
		String paymentKey, String orderId, int amount, CancelReason reason
	) {
		log.info("[결제-Retry] 결제 취소 요청 정보 DB 저장 시도: orderId={}", orderId);
		return persistenceService.requestCancel(orderId, reason, amount);
	}

	/**
	 * 결제 취소 요청 저장 실패 시 보상 트랜잭션
	 * - 내부 보상 트랜잭션 실행
	 */
	public PaymentCancelResponse recoverCancelRequest(
		String paymentKey, String orderId, int amount, CancelReason reason, Throwable t
	) {
		log.error("[결제-Retry] 결제 취소 요청 DB 저장 실패, 보상 트랜잭션 실행: orderId={}, cause={}", orderId, t.toString());
		return compensationTxService.compensateCancelRequestFailure(orderId, t);
	}

	/**
	 * 1) 사용자 요청 결제 취소 완료 내역을 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> compensateCancelCompleteFailure에서 보상 트랜잭션 실행
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverCancelComplete")
	public PaymentCancelResponse saveCancelComplete(String orderId) {
		log.info("[결제-Retry] 결제 취소 완료 정보 DB 저장 시도: orderId={}", orderId);
		return persistenceService.completeCancel(orderId);
	}

	/**
	 * 결제 취소 완료 저장 실패 시 보상 트랜잭션
	 * - 내부 보상 트랜잭션 실행
	 */
	public PaymentCancelResponse recoverCancelComplete(String orderId, Throwable t) {
		log.error("[결제-Retry] 결제 취소 완료 DB 저장 실패, 보상 트랜잭션 실행: orderId={}, cause={}", orderId, t.toString());
		return compensationTxService.compensateCancelCompleteFailure(orderId, t);
	}

	/**
	 * 1) 자동결제 빌링키를 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> compensateIssueKeyFailure에서 예외 전파
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverIssueKey")
	public PaymentIssueBillingKeyResponse saveBillingKey(String orderId, String billingKey) {
		log.info("[결제-Retry] 빌링키 발급 정보 DB 저장 시도: orderId={}", orderId);
		return persistenceService.saveBillingKeyRegistration(orderId, billingKey);
	}

	/**
	 * 빌링키 발급 DB저장 실패 시 (보상 없음, 예외만)
	 */
	public PaymentIssueBillingKeyResponse recoverIssueKey(String orderId, String billingKey, Throwable t) {
		log.error("[결제-Retry] 빌링키 발급 DB 저장 실패: orderId={}, cause={}", orderId, t.toString());
		compensationTxService.compensateIssueKeyFailure(orderId, billingKey, t);
		return null; // 위에서 예외를 throw 하므로 사실상 도달 불가
	}

	/**
	 * 1) 자동결제 결과를 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> compensateAutoChargeFailure에서 보상(자동 취소) 실행
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverAutoCharge")
	public PaymentConfirmResponse saveAutoCharge(
		String billingKey, String orderId, int amount, TossBillingChargeResponse tossRes
	) {
		log.info("[결제-Retry] 자동결제 승인 결과 DB 저장 시도: orderId={}", orderId);
		return persistenceService.saveAutoChargeResult(orderId, tossRes);
	}

	/**
	 * 자동결제 결과 저장 실패 시 보상(자동 취소)
	 * - 외부 결제 취소 후 내부 보상 트랜잭션 실행
	 */
	public PaymentConfirmResponse recoverAutoCharge(
		String billingKey, String orderId, int amount, TossBillingChargeResponse tossRes, Throwable t
	) {
		log.error("[결제-Retry] 자동결제 승인 결과 DB 저장 실패, 보상 트랜잭션 실행: orderId={}, cause={}", orderId, t.toString());
		gatewayPort.cancelPayment(
			billingKey,
			CancelReason.SYSTEM_ERROR.name(),
			amount,
			"보상-자동 결제 취소"
		);
		compensationTxService.compensateAutoChargeFailure(orderId, t);
		throw new IllegalStateException("자동결제 보상 완료", t);
	}
}