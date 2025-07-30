package com.grow.payment_service.payment.saga;

import org.springframework.stereotype.Service;
import io.github.resilience4j.retry.annotation.Retry;

import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.payment.global.exception.PaymentSagaException;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingChargeResponse;
import com.grow.payment_service.payment.global.exception.ErrorCode;

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
	 * 2) 저장 실패 시 3회 재시도 -> recoverConfirm에서 보상(자동 취소) 실행
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverConfirm")
	public Long saveConfirmation(String paymentKey, String orderId, int amount) {
		log.info("[결제-Retry] 결제 승인 정보 DB 저장 시도: orderId={}", orderId);
		return persistenceService.savePaymentConfirmation(orderId);
	}

	/**
	 * 결제 승인 정보 저장 실패 시 보상(자동 취소)
	 * - 외부 결제 보상 취소 요청 후 내부 보상 트랜잭션 실행
	 * - 보상 완료 시 SAGA_COMPENSATE_COMPLETED 예외 전파
	 */
	public Long recoverConfirm(String paymentKey, String orderId, int amount, Throwable t) {
		log.error("[결제-Retry] 결제 승인 DB 저장 실패, 보상 트랜잭션 실행: orderId={}, cause={}", orderId, t.toString());
		try {
			gatewayPort.cancelPayment(paymentKey, CancelReason.SYSTEM_ERROR.name(), amount, "보상 취소");
			compensationTxService.compensateApprovalFailure(orderId, t);
		} catch (Exception ex) {
			// 보상 중 에러 발생 시
			throw new PaymentSagaException(ErrorCode.SAGA_COMPENSATE_ERROR, ex);
		}
		// 보상 완료 알림
		throw new PaymentSagaException(ErrorCode.SAGA_COMPENSATE_COMPLETED, t);
	}

	/**
	 * 1) 사용자 요청 결제 취소 요청 내역을 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> recoverCancelRequest에서 보상 트랜잭션 실행
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverCancelRequest")
	public PaymentCancelResponse saveCancelRequest(
		String paymentKey, String orderId, int amount, CancelReason reason
	) {
		log.info("[결제-Retry] 결제 취소 요청 정보 DB 저장 시도: orderId={}", orderId);
		return persistenceService.requestCancel(orderId, reason, amount);
	}

	public PaymentCancelResponse recoverCancelRequest(
		String paymentKey, String orderId, int amount, CancelReason reason, Throwable t
	) {
		log.error("[결제-Retry] 결제 취소 요청 DB 저장 실패, 보상 트랜잭션 실행: orderId={}, cause={}", orderId, t.toString());
		return compensationTxService.compensateCancelRequestFailure(orderId, t);
	}

	/**
	 * 1) 사용자 요청 결제 취소 완료 내역을 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> recoverCancelComplete에서 보상 트랜잭션 실행
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverCancelComplete")
	public PaymentCancelResponse saveCancelComplete(String orderId) {
		log.info("[결제-Retry] 결제 취소 완료 정보 DB 저장 시도: orderId={}", orderId);
		return persistenceService.completeCancel(orderId);
	}

	public PaymentCancelResponse recoverCancelComplete(String orderId, Throwable t) {
		log.error("[결제-Retry] 결제 취소 완료 DB 저장 실패, 보상 트랜잭션 실행: orderId={}, cause={}", orderId, t.toString());
		return compensationTxService.compensateCancelCompleteFailure(orderId, t);
	}

	/**
	 * 1) 자동결제 빌링키를 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> recoverIssueKey에서 예외 전파
	 */
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverIssueKey")
	public PaymentIssueBillingKeyResponse saveBillingKey(String orderId, String billingKey) {
		log.info("[결제-Retry] 빌링키 발급 정보 DB 저장 시도: orderId={}", orderId);
		return persistenceService.saveBillingKeyRegistration(orderId, billingKey);
	}

	public PaymentIssueBillingKeyResponse recoverIssueKey(String orderId, String billingKey, Throwable t) {
		log.error("[결제-Retry] 빌링키 발급 DB 저장 실패: orderId={}, cause={}", orderId, t.toString());
		compensationTxService.compensateIssueKeyFailure(orderId, billingKey, t);
		// 보상 후 예외가 던져지므로 도달하지 않음
		throw new PaymentSagaException(ErrorCode.SAGA_COMPENSATE_COMPLETED, t);
	}

	/**
	 * 1) 자동결제 결과를 DB에 저장
	 * 2) 저장 실패 시 3회 재시도 -> recoverAutoCharge에서 보상(자동 취소) 실행
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
	 * - 보상 완료 시 SAGA_COMPENSATE_COMPLETED 예외 전파
	 */
	public PaymentConfirmResponse recoverAutoCharge(
		String billingKey, String orderId, int amount, TossBillingChargeResponse tossRes, Throwable t
	) {
		log.error("[결제-Retry] 자동결제 승인 결과 DB 저장 실패, 보상 트랜잭션 실행: orderId={}, cause={}", orderId, t.toString());
		try {
			gatewayPort.cancelPayment(
				billingKey,
				CancelReason.SYSTEM_ERROR.name(),
				amount,
				"보상-자동 결제 취소"
			);
			compensationTxService.compensateAutoChargeFailure(orderId, t);
		} catch (Exception ex) {
			throw new PaymentSagaException(ErrorCode.SAGA_COMPENSATE_ERROR, ex);
		}
		throw new PaymentSagaException(ErrorCode.SAGA_COMPENSATE_COMPLETED, t);
	}
}