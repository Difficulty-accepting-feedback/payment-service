package com.grow.payment_service.payment.saga;

import org.springframework.stereotype.Service;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.global.exception.PaymentSagaException;
import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingAuthResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingChargeResponse;
import com.grow.payment_service.payment.infra.redis.RedisIdempotencyAdapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSagaOrchestrator {

	private final PaymentGatewayPort gatewayPort;
	private final RetryablePersistenceService retryableService;
	private final RedisIdempotencyAdapter idempotencyAdapter;
	private final PaymentPersistenceService persistenceService;

	/**
	 * 1) 멱등키 reserve/getResult 로직으로 중복 처리 방지
	 * 2) 토스 결제 승인 API 호출
	 * 3) DB 저장(리트라이+보상)
	 * 4) 처리 완료 후 결과 저장
	 */
	public Long confirmWithCompensation(
		String paymentKey,
		String orderId,
		int amount,
		String idempotencyKey,
		String customerEmail,
		String customerName
	) {
		log.info("[SAGA][confirm] 시작 → paymentKey={}, orderId={}, amount={}, idempotencyKey={}, email={}, name={}",
			paymentKey, orderId, amount, idempotencyKey, customerEmail, customerName);

		log.debug("[SAGA][confirm] 멱등키 예약 시도 → key={}", idempotencyKey);
		if (!idempotencyAdapter.reserve(idempotencyKey)) {
			log.warn("[SAGA][confirm] 중복 요청 차단 → key={}", idempotencyKey);
			String prev = idempotencyAdapter.getResult(idempotencyKey);
			if (prev != null) {
				log.info("[SAGA][confirm] 이전 처리 결과 반환 → paymentId={}", prev);
				return Long.valueOf(prev);
			}
			throw new PaymentSagaException(ErrorCode.IDEMPOTENCY_IN_FLIGHT);
		}
		log.debug("[SAGA][confirm] 멱등키 예약 성공 → key={}", idempotencyKey);

		try {
			log.info("[SAGA][confirm] 토스 API 호출 → confirmPayment(paymentKey={}, orderId={}, amount={})",
				paymentKey, orderId, amount);
			gatewayPort.confirmPayment(paymentKey, orderId, amount, customerEmail, customerName);
			log.info("[SAGA][confirm] 토스 API 호출 완료");

			log.info("[SAGA][confirm] DB 저장(saveConfirmation) 시작 → orderId={}", orderId);
			Long paymentId = retryableService.saveConfirmation(paymentKey, orderId, amount);
			log.info("[SAGA][confirm] DB 저장 완료 → paymentId={}", paymentId);

			log.debug("[SAGA][confirm] 멱등키 완료 처리 → key={}, paymentId={}", idempotencyKey, paymentId);
			idempotencyAdapter.finish(idempotencyKey, paymentId.toString());

			log.info("[SAGA][confirm] 종료 → paymentId={}", paymentId);
			return paymentId;

		} catch (Exception ex) {
			log.error("[SAGA][confirm] 에러 발생, 멱등키 무효화 → key={}, error={}", idempotencyKey, ex.getMessage(), ex);
			idempotencyAdapter.invalidate(idempotencyKey);
			throw ex;
		}
	}
	/**
	 * 1) 멱등키 reserve/getResult 로직으로 중복 처리 방지
	 * 2) 토스 자동결제 API 호출
	 * 3) DB 저장(리트라이+보상)
	 * 4) 처리 완료 후 결과 저장
	 */
	public PaymentConfirmResponse autoChargeWithCompensation(
		PaymentAutoChargeParam param,
		String idempotencyKey
	) {
		// 멱등 키 생성 또는 이전 결과 조회
		if (!idempotencyAdapter.reserve(idempotencyKey)) {
			// 이미 같은 키가 처리중 or 완료
			String prev = idempotencyAdapter.getResult(idempotencyKey);
			if (prev != null) {
				// 이전에 성공한 결과 반환
				var existing = persistenceService.findByOrderId(param.getOrderId());
				return new PaymentConfirmResponse(
					Long.valueOf(prev),
					existing.getPayStatus().name(),
					param.getCustomerEmail(),
					param.getCustomerName()
				);
			}
			// 아직 처리 중이면 중복 요청 예외 발생
			throw new PaymentSagaException(ErrorCode.IDEMPOTENCY_IN_FLIGHT);
		}

		try {
			// ✅ null-safe 기본값 처리 (NPE 방지)
			final int taxFree   = (param.getTaxFreeAmount() == null) ? 0 : param.getTaxFreeAmount();
			final int taxExempt = (param.getTaxExemptionAmount() == null) ? 0 : param.getTaxExemptionAmount();

			// 토스 자동결제 API 호출
			TossBillingChargeResponse toss = gatewayPort.chargeWithBillingKey(
				param.getBillingKey(),
				param.getCustomerKey(),
				param.getAmount(),
				param.getOrderId(),
				param.getOrderName(),
				param.getCustomerEmail(),
				param.getCustomerName(),
				taxFree,          // ← null이면 0
				taxExempt         // ← null이면 0
			);

			// DB 저장(리트라이+보상)
			PaymentConfirmResponse res = retryableService.saveAutoCharge(
				param.getBillingKey(),
				param.getOrderId(),
				param.getAmount(),
				toss
			);
			// 멱등 키 완료 처리
			idempotencyAdapter.finish(idempotencyKey, String.valueOf(res.getPaymentId()));
			return res;
		} catch (Exception ex) {
			// 처리 중 예외 발생하면 멱등 키 리셋
			idempotencyAdapter.invalidate(idempotencyKey);
			throw ex;
		}
	}

	/**
	 * 1) DB 저장(취소 요청, 리트라이+보상)
	 * 2) 토스 결제 취소 API 호출
	 * 3) DB 저장(취소 완료, 리트라이+보상)
	 */
	public PaymentCancelResponse cancelWithCompensation(
		String paymentKey,
		String orderId,
		int amount,
		CancelReason reason
	) {
		retryableService.saveCancelRequest(paymentKey, orderId, amount, reason);
		gatewayPort.cancelPayment(paymentKey, reason.name(), amount, "사용자 요청 취소");
		return retryableService.saveCancelComplete(orderId);
	}

	/**
	 * 1) 토스 빌링키 발급 API 호출
	 * 2) DB 저장(리트라이+보상)
	 */
	public PaymentIssueBillingKeyResponse issueKeyWithCompensation(
		PaymentIssueBillingKeyParam param
	) {
		TossBillingAuthResponse toss = gatewayPort.issueBillingKey(
			param.getAuthKey(),
			param.getCustomerKey()
		);
		return retryableService.saveBillingKey(param.getOrderId(), toss.getBillingKey());
	}
}