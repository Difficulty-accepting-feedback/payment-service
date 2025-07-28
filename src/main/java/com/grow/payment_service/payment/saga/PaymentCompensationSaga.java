package com.grow.payment_service.payment.saga;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.retry.annotation.Retry;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.service.PaymentApplicationService;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompensationSaga {

	private final PaymentApplicationService paymentService;

	// 결제 승인 -> DB 재시도 -> 보상(취소)
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverConfirm")
	@Transactional
	public Long confirmWithCompensation(
		String paymentKey,
		String orderId,
		int amount
	) {
		return paymentService.confirmPayment(paymentKey, orderId, amount);
	}

	public Long recoverConfirm(
		String paymentKey,
		String orderId,
		int amount,
		Throwable throwable
	) {
		log.error("[SAGA-Recover] 결제 승인 실패, 보상 취소 실행: orderId={}", orderId, throwable);
		try {
			paymentService.cancelPayment(
				paymentKey,
				orderId,
				amount,
				CancelReason.SYSTEM_ERROR
			);
			log.info("[SAGA-Recover] 보상 결제 취소 완료: orderId={}", orderId);
		} catch (Exception cancelEx) {
			log.error("[SAGA-Recover] 보상 결제 취소 실패: orderId={}", orderId, cancelEx);
		}
		throw new IllegalStateException("결제 승인 재시도 -> 취소 처리되었습니다.", throwable);
	}

	// 빌링키 발급 -> DB 재시도 -> 알림
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverIssueKey")
	@Transactional
	public String issueKeyWithCompensation(PaymentIssueBillingKeyParam param) {
		return paymentService.issueBillingKey(param).getBillingKey();
	}

	public String recoverIssueKey(
		PaymentIssueBillingKeyParam param,
		Throwable throwable
	) {
		log.error("[SAGA-Recover] 빌링키 발급 실패: orderId={}", param.getOrderId(), throwable);
		throw new IllegalStateException("빌링키 발급 재시도 실패", throwable);
	}

	// 자동결제 승인 -> DB 재시도 -> 보상(자동결제 취소)
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverAutoCharge")
	@Transactional
	public PaymentConfirmResponse autoChargeWithCompensation(PaymentAutoChargeParam param) {
		return paymentService.chargeWithBillingKey(param);
	}

	public PaymentConfirmResponse recoverAutoCharge(
		PaymentAutoChargeParam param,
		Throwable throwable
	) {
		log.error("[SAGA-Recover] 자동결제 승인 실패, 보상 취소 실행: orderId={}", param.getOrderId(), throwable);
		try {
			paymentService.cancelPayment(
				param.getBillingKey(),
				param.getOrderId(),
				param.getAmount(),
				CancelReason.SYSTEM_ERROR
			);
			log.info("[SAGA-Recover] 자동결제 취소 보상 완료: orderId={}", param.getOrderId());
		} catch (Exception cancelEx) {
			log.error("[SAGA-Recover] 자동결제 취소 보상 실패: orderId={}", param.getOrderId(), cancelEx);
		}
		throw new IllegalStateException("자동결제 재시도 실패 -> 취소 처리되었습니다.", throwable);
	}

	// 사용자 직접 취소 -> DB 재시도 -> 알림
	@Retry(name = "dataSaveInstance", fallbackMethod = "recoverCancel")
	@Transactional
	public void cancelWithCompensation(
		String paymentKey,
		String orderId,
		int amount,
		CancelReason reason
	) {
		paymentService.cancelPayment(paymentKey, orderId, amount, reason);
	}

	public void recoverCancel(
		String paymentKey,
		String orderId,
		int amount,
		CancelReason reason,
		Throwable throwable
	) {
		log.error("[SAGA-Recover] 취소 처리 실패: orderId={}", orderId, throwable);
		throw new IllegalStateException("취소 처리 재시도 실패", throwable);
	}
}