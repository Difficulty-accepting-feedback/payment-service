package com.grow.payment_service.payment.application.service.impl;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	 * 매월 자동결제 배치 실행
	 * 이 메서드는 READY 상태 결제 건을 조회하고,
	 * 각 건에 대해 즉시 자동결제(processSingleAutoCharge)를 호출합니다.
	 * 1. READY 상태인 결제 건 조회
	 * 2. idempotency 키 생성 및 중복 처리 방지
	 * 3. READY -> IN_PROGRESS 전이 및 이력 저장
	 * 4. 외부 과금 호출
	 * 5. IN_PROGRESS -> APPROVED 전이 및 이력 저장
	 * 6. APPROVED -> READY 리셋(다음 달 준비) 및 이력 저장
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
				log.warn("[중복 자동결제 차단] idempotencyKey={}", idempotencyKey);
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
					.orderName("GROW Plan #" + inProgress.getOrderId())
					.customerEmail("member" + inProgress.getMemberId() + "@example.com")
					.customerName("Member " + inProgress.getMemberId())
					.build();

				PaymentConfirmResponse res =
					paymentService.chargeWithBillingKey(param, idempotencyKey);
				log.info("[자동결제 성공] idempotencyKey={}, 결제ID={}, 주문ID={}, 결과={}",
					idempotencyKey, p.getPaymentId(), p.getOrderId(), res.getPayStatus());

				// IN_PROGRESS → APPROVED 전이
				Payment approved = inProgress.approveAutoBilling();
				paymentRepository.save(approved);
				historyRepository.save(PaymentHistory.create(
					approved.getPaymentId(),
					approved.getPayStatus(),
					"자동결제 승인 처리"
				));

				// APPROVED -> READY 리셋 (다음 달 결제 준비)
				Payment ready = approved.resetForNextCycle();
				paymentRepository.save(ready);
				historyRepository.save(PaymentHistory.create(
					ready.getPaymentId(),
					ready.getPayStatus(),
					"다음 달 READY로 전이"
				));

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

	/**
	 * 재시도 한계를 초과한 자동결제에 대해 실패 처리 및 빌링키를 제거합니다.
	 */
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

	/**
	 * 단일 결제 건에 대해 자동결제 시도
	 * Quartz JobListener를 활용한 재시도 로직을 위해 분리된 메서드입니다.
	 * 1. 결제 정보 조회
	 * 2. idempotency 키 생성 및 예약
	 * 3. READY -> IN_PROGRESS 전이 및 이력 저장
	 * 4. 외부 과금 호출
	 * 5. IN_PROGRESS -> APPROVED 전이 및 이력 저장
	 * 6. APPROVED -> READY 리셋(다음 달 준비) 및 이력 저장
	 */
	@Override
	@Transactional
	public void processSingleAutoCharge(Long paymentId) {
		Payment p = paymentRepository.findById(paymentId)
			.orElseThrow(() -> new PaymentApplicationException(
				ErrorCode.BATCH_AUTO_CHARGE_ERROR
			));

		String billingMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
		String recordKey = "autoCharge:" + p.getOrderId() + ":" + billingMonth;
		String idemKey = idempotencyAdapter.getOrCreateKey(recordKey);

		if (!idempotencyAdapter.reserve(idemKey)) {
			log.warn("[중복 자동결제 차단] paymentId={}, idemKey={}", paymentId, idemKey);
			return;
		}

		try {
			// READY -> IN_PROGRESS 전이
			Payment inProgress = p.startAutoBilling();
			paymentRepository.save(inProgress);
			historyRepository.save(PaymentHistory.create(
				inProgress.getPaymentId(),
				inProgress.getPayStatus(),
				"자동결제 진행 중 상태로 전이"
			));

			// 2) 외부 과금 호출
			PaymentAutoChargeParam param = PaymentAutoChargeParam.builder()
				.billingKey(inProgress.getBillingKey())
				.customerKey(inProgress.getCustomerKey())
				.amount(inProgress.getTotalAmount().intValue())
				.orderId(inProgress.getOrderId())
				.orderName("GROW Plan #" + inProgress.getOrderId())
				.customerEmail("member" + inProgress.getMemberId() + "@example.com")
				.customerName("Member " + inProgress.getMemberId())
				.build();

			PaymentConfirmResponse res =
				paymentService.chargeWithBillingKey(param, idemKey);
			log.info("[자동결제 성공] paymentId={}, idemKey={}, 결과={}",
				paymentId, idemKey, res.getPayStatus());

			// IN_PROGRESS -> APPROVED 전이
			Payment approved = inProgress.approveAutoBilling();
			paymentRepository.save(approved);
			historyRepository.save(PaymentHistory.create(
				approved.getPaymentId(),
				approved.getPayStatus(),
				"자동결제 승인 처리"
			));

			// APPROVED -> READY 리셋 (다음 달 결제 준비)
			Payment ready = approved.resetForNextCycle();
			paymentRepository.save(ready);
			historyRepository.save(PaymentHistory.create(
				ready.getPaymentId(),
				ready.getPayStatus(),
				"다음 달 READY로 전이"
			));

		} catch (Exception ex) {
			log.error("[자동결제 실패] paymentId={}, 원인={}", paymentId, ex.getMessage(), ex);
			throw new PaymentApplicationException(
				ErrorCode.BATCH_AUTO_CHARGE_ERROR, ex
			);
		}
	}
}