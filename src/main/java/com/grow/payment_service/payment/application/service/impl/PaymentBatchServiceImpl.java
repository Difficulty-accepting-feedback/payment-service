package com.grow.payment_service.payment.application.service.impl;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.service.PaymentApplicationService;
import com.grow.payment_service.payment.application.service.PaymentBatchService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.FailureReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.global.exception.ErrorCode;
import com.grow.payment_service.payment.global.exception.PaymentApplicationException;
import com.grow.payment_service.payment.infra.paymentprovider.TossPaymentClient;
import com.grow.payment_service.payment.infra.redis.RedisIdempotencyAdapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentBatchServiceImpl implements PaymentBatchService {

	private final PaymentRepository paymentRepository;
	private final PaymentHistoryRepository historyRepository;
	private final PaymentApplicationService paymentService;
	private final RedisIdempotencyAdapter idempotencyAdapter;

	/**
	 * 매월 자동결제 대상에 대해 실제 과금을 시도합니다.
	 * 상태가 AUTO_BILLING_READY 인 결제만 처리
	 */
	@Override
	public void processMonthlyAutoCharge() {
		List<Payment> targets = paymentRepository
			.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_READY);

		if (targets.isEmpty()) {
			log.info("[자동결제 대상 없음] 처리할 결제 건이 없습니다.");
			return;
		}

		String billingMonth = YearMonth.now()
			.format(DateTimeFormatter.ofPattern("yyyy-MM"));

		for (Payment p : targets) {
			// recordKey : "autoCharge:{orderId}:{billingMonth}"
			// 한 결제 건(orderId)과 한 청구월(billingMonth) 당
			// 한 번만 생성되는 UUID 멱등키를 관리하기 위한 고유 식별자
			String recordKey = "autoCharge:" + p.getOrderId() + ":" + billingMonth;

			// UUID 멱등키를 한 번만 생성(getOrCreateKey) -> idempotencyKey
			String idempotencyKey = idempotencyAdapter.getOrCreateKey(recordKey);

			// 예약(reserve) 검사 -> false 면 이미 처리된 키
			if (!idempotencyAdapter.reserve(idempotencyKey)) {
				log.warn("[중복 자동결제 차단] idemKey={}", idempotencyKey);
				continue;
			}
			try {

				// 상태 전이: AUTO_BILLING_READY -> IN_PROGRESS
				Payment inProgress = p.startAutoBilling();
				paymentRepository.save(inProgress);
				historyRepository.save(PaymentHistory.create(
					inProgress.getPaymentId(),
					inProgress.getPayStatus(),
					"자동결제 진행 중 상태로 전이"
				));

				// 자동결제 파라미터 생성
				PaymentAutoChargeParam param = PaymentAutoChargeParam.builder()
					.billingKey(inProgress.getBillingKey())
					.customerKey(inProgress.getCustomerKey())
					.amount(inProgress.getTotalAmount().intValue())
					.orderId(inProgress.getOrderId())
					.orderName("GROW Plan #" +inProgress.getOrderId())
					.customerEmail("member" + inProgress.getMemberId() + "@example.com")
					.customerName("Member " + inProgress.getMemberId())
					.taxFreeAmount(null)
					.taxExemptionAmount(null)
					.build();

				// idempotencyKey 함께 전달
				PaymentConfirmResponse res =
					paymentService.chargeWithBillingKey(param, idempotencyKey);

				log.info("[자동결제 성공] idempotencyKey={}, 결제ID={}, 주문ID={}, 결과={}",
					idempotencyKey, p.getPaymentId(), p.getOrderId(), res.getPayStatus());
			} catch (Exception ex) {
				log.error("[자동결제 실패] idempotencyKey={}, 결제ID={}, 주문ID={}, 원인={}",
					idempotencyKey, p.getPaymentId(), p.getOrderId(), ex.getMessage(), ex);
				throw new PaymentApplicationException(
					ErrorCode.BATCH_AUTO_CHARGE_ERROR, ex
				);
			}
		}
	}

	/**
	 * 특정 회원의 payment 객체에서 빌링키를 제거합니다.
	 * 빌링키가 있는 결제만 처리
	 */
	@Override
	public void removeBillingKeysForMember(Long memberId) {
		List<Payment> list = paymentRepository.findAllByMemberId(memberId);

		if (list.isEmpty()) {
			log.info("[빌링키 제거 대상 없음] memberId={}", memberId);
			return;
		}

		for (Payment p : list) {
			// 빌링 키가 있는 경우만 처리
			if (p.getBillingKey() != null) {
				log.info("[빌링키 제거 시작] 결제ID={}, 기존BillingKey={}",
					p.getPaymentId(), p.getBillingKey());

				try {
					// 빌링 키 제거
					Payment updated = p.clearBillingKey();
					// 변경된 결제 저장
					paymentRepository.save(updated);
					// 히스토리 기록
					historyRepository.save(
						PaymentHistory.create(
							updated.getPaymentId(),
							updated.getPayStatus(),
							"빌링키 제거"
						)
					);
					log.info("[빌링키 제거 완료] 결제ID={}, billingKey=null 로 변경",
						updated.getPaymentId());
				} catch (Exception ex) {
					log.error("[빌링키 제거 실패] 결제ID={}, 원인={}",
						p.getPaymentId(), ex.getMessage(), ex);
					throw new PaymentApplicationException(
						ErrorCode.BATCH_CLEAR_BILLINGKEY_ERROR, ex
					);
				}
			}
		}
	}

	@Override
	public void markAutoChargeFailedPermanently() {
		// AUTO_BILLING_IN_PROGRESS 상태의 결제 목록 조회
		List<Payment> targets = paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(
			PayStatus.AUTO_BILLING_IN_PROGRESS);

		// 각 결제에 대해 실패 상태로 전이
		for (Payment p : targets) {
			Payment failed = p.failAutoBilling(FailureReason.RETRY_EXCEEDED);
			Payment cleared = failed.clearBillingKey(); // 빌링키 제거

			paymentRepository.save(cleared);
			historyRepository.save(
				PaymentHistory.create(
					cleared.getPaymentId(),
					cleared.getPayStatus(),
					"자동결제 재시도 한계 도달 -> 실패 처리 및 빌링키 제거"
				)
			);
		}
		log.info("[자동결제] 5회 재시도 후 실패 상태 전이 완료: count={}", targets.size());
	}
}