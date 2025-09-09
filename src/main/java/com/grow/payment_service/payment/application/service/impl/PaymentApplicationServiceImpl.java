package com.grow.payment_service.payment.application.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.PaymentApplicationException;
import com.grow.payment_service.payment.application.dto.*;
import com.grow.payment_service.payment.application.event.PaymentNotificationProducer;
import com.grow.payment_service.payment.application.service.PaymentApplicationService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.domain.service.OrderIdGenerator;
import com.grow.payment_service.payment.infra.client.MemberClient;
import com.grow.payment_service.payment.infra.client.MemberInfoResponse;
import com.grow.payment_service.payment.saga.PaymentSagaOrchestrator;
import com.grow.payment_service.plan.domain.model.Plan;
import com.grow.payment_service.plan.domain.repository.PlanRepository;
import com.grow.payment_service.subscription.application.service.SubscriptionHistoryApplicationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApplicationServiceImpl implements PaymentApplicationService {

	private static final String SUCCESS_URL = "http://localhost:3000/me/payment/success";
	private static final String FAIL_URL = "http://localhost:3000/me/payment/fail";

	private final PlanRepository planRepository;
	private final OrderIdGenerator orderIdGenerator;
	private final PaymentRepository paymentRepository;
	private final PaymentHistoryRepository historyRepository;
	private final PaymentSagaOrchestrator paymentSaga;
	private final SubscriptionHistoryApplicationService subscriptionService;
	private final MemberClient memberClient;
	private final PaymentNotificationProducer notificationProducer;


	/**
	 * 주문 DB 생성 후 클라이언트에게 데이터 반환
	 */
	@Override
	@Transactional
	public PaymentInitResponse initPaymentData(
		Long memberId, Long planId, int amount
	) {
		log.info("[주문 생성 요청 시작] memberId={}, planId={}, amount={}",
			memberId, planId, amount);

		try {
			// [1/3] Redis를 이용해 고유 orderId 생성
			log.info("[1/3] orderId 생성 중... memberId={}", memberId);
			String orderId = orderIdGenerator.generate(memberId);

			// [2/3] Payment & History 저장
			log.info("[2/3] 결제 엔티티 저장 및 이력 기록 → orderId={}", orderId);
			Payment payment = Payment.create(
				memberId, planId, orderId,
				null, null,
				"cust_" + memberId,
				(long)amount,
				"CARD"
			);
			payment = paymentRepository.save(payment);
			historyRepository.save(
				PaymentHistory.create(
					payment.getPaymentId(),
					payment.getPayStatus(),
					"주문 생성"
				)
			);

			// [3/3] Plan 조회 및 응답 생성
			log.info("[3/3] Plan 조회 → planId={}", planId);
			Plan plan = planRepository.findById(planId)
				.orElseThrow(() -> new PaymentApplicationException(
					ErrorCode.PAYMENT_INIT_ERROR
				));

			return new PaymentInitResponse(
				orderId,
				amount,
				"GROW Plan #" + orderId,
				SUCCESS_URL,
				FAIL_URL,
				planId,
				plan.getType(),
				plan.getPeriod()
			);
		} catch (Exception ex) {
			log.error("주문 생성 실패: memberId={}, planId={}, amount={}",
				memberId, planId, amount, ex);
			throw new PaymentApplicationException(ErrorCode.PAYMENT_INIT_ERROR, ex);
		}
	}

	/**
	 * 토스 위젯이 발급한 paymentKey 로 승인 처리
	 */
	@Override
	@Transactional
	public Long confirmPayment(
		Long memberId,
		String paymentKey,
		String orderId,
		int amount,
		String idempotencyKey
	) {
		log.info("[결제 승인 요청 시작] memberId={}, orderId={}, amount={}, paymentKey={}",
			memberId, orderId, amount, paymentKey);

		// [1/4] 멤버 서비스 호출 → 이메일·이름 조회
		log.info("[1/4] 멤버 서비스 호출 중... memberId={}", memberId);
		RsData<MemberInfoResponse> rs = memberClient.getMyInfo(memberId);
		MemberInfoResponse profile = rs.getData();
		String customerEmail = profile.getEmail();
		String customerName = profile.getNickname();
		log.info("[1/4] 멤버 정보 조회 완료 → email={}, nickname={}",
			customerEmail, customerName);

		// [2/4] SAGA 결제 승인 호출
		log.info("[2/4] SAGA 호출 → paymentKey={}, orderId={}, amount={}, idempotencyKey={}, email={}, name={}",
			paymentKey, orderId, amount, idempotencyKey, customerEmail, customerName);
		Long paymentId = paymentSaga.confirmWithCompensation(
			paymentKey,
			orderId,
			amount,
			idempotencyKey,
			customerEmail,
			customerName
		);
		log.info("[2/4] SAGA 결제 승인 완료 → paymentId={}", paymentId);

		// [3/4] 주문 조회 & 소유권 검증
		log.info("[3/4] 주문 조회 및 소유권 검증 → paymentId={}", paymentId);
		Payment paid = paymentRepository.findById(paymentId)
			.orElseThrow(() -> new PaymentApplicationException(ErrorCode.PAYMENT_NOT_FOUND));
		paid.verifyOwnership(memberId);
		log.info("[3/4] 소유권 검증 완료 → memberId={} owns paymentId={}",
			memberId, paymentId);

		// 결제 승인 알림
		notificationProducer.paymentApproved(memberId, orderId, amount);

		// [4/4] 구독 플랜 갱신 처리
		log.info("[4/4] Plan 조회 → planId={}", paid.getPlanId());
		Plan plan = planRepository.findById(paid.getPlanId())
			.orElseThrow(() -> new PaymentApplicationException(ErrorCode.PAYMENT_INIT_ERROR));
		if (plan.isAutoRenewal()) {
			subscriptionService.recordSubscriptionRenewal(memberId, plan.getPeriod());
			log.info("[4/4] 구독 갱신 기록 완료 → memberId={}, period={}",
				memberId, plan.getPeriod());
		} else {
			log.info("[4/4] 자동 갱신 대상 아님 (One-time purchase)");
		}

		return paymentId;
	}

	/**
	 * 결제 취소 요청 처리
	 */
	@Override
	@Transactional
	public PaymentCancelResponse cancelPayment(
		Long memberId,
		String orderId,
		int cancelAmount,
		CancelReason reason
	) {
		log.info("[결제 취소 요청 시작] memberId={}, orderId={}, cancelAmount={}, reason={}",
			memberId, orderId, cancelAmount, reason);

		// [1/2] 주문 조회 & 소유권 검증
		log.info("[1/2] 주문 조회 및 소유권 검증 → orderId={}", orderId);
		Payment paid = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new PaymentApplicationException(ErrorCode.ORDER_NOT_FOUND));
		paid.verifyOwnership(memberId);
		log.info("[1/2] 소유권 검증 완료 → memberId={} owns orderId={}",
			memberId, orderId);

		// 플랜 조회 (구독 여부 판단용)
		Plan plan = planRepository.findById(paid.getPlanId())
			.orElseThrow(() -> new PaymentApplicationException(ErrorCode.PAYMENT_INIT_ERROR));

		// 구독(자동결제) 플랜이면 7일 환불 정책 적용
		// - 7일 이내: 전액 환불(토스 취소)
		// - 7일 초과: 다음달부터 해지(빌링키 제거)
		// ─────────────────────────────────────────────────────────────
		if (plan.isAutoRenewal()) {
			var lastApprovalOpt = historyRepository.findLastByPaymentIdAndStatuses(
				paid.getPaymentId(),
				List.of(PayStatus.DONE, PayStatus.AUTO_BILLING_APPROVED)
			);

			boolean within7Days = false;
			if (lastApprovalOpt.isPresent()) {
				LocalDateTime approvedAt = lastApprovalOpt.get().getChangedAt();
				within7Days = !approvedAt.plusDays(7).isBefore(LocalDateTime.now());
			}

			if (within7Days) {
				// ▶ 7일 이내 = 전액 환불 (토스 취소)
				String paymentKey = paid.getPaymentKey();
				if (paymentKey == null || paymentKey.isBlank()) {
					// 자동결제 승인 시 paymentKey를 저장하지 않았다면 환불 불가 → 에러 처리
					log.error("취소 불가: paymentKey 없음 → orderId={}", orderId);
					throw new PaymentApplicationException(ErrorCode.PAYMENT_CANCEL_ERROR);
				}
				int fullAmount = paid.getTotalAmount() == null
					? cancelAmount
					: paid.getTotalAmount().intValue();

				// [2/2] SAGA 결제 취소 호출
				log.info("[2/2] SAGA 결제 취소 호출 → paymentKey={}, orderId={}, cancelAmount={}, reason={}",
					paymentKey, orderId, fullAmount, reason);
				try {
					PaymentCancelResponse res = paymentSaga.cancelWithCompensation(
						paymentKey, orderId, fullAmount, reason
					);
					log.info("[2/2] SAGA 결제 취소 완료 → paymentKey={}, orderId={}",
						paymentKey, orderId);

					// 결제 취소 알림
					notificationProducer.cancelled(memberId, orderId, fullAmount);

					return res;
				} catch (Exception ex) {
					log.error("결제 취소 실패: paymentKey={}, orderId={}, cancelAmount={}",
						paymentKey, orderId, fullAmount, ex);
					throw new PaymentApplicationException(
						ErrorCode.PAYMENT_CANCEL_ERROR, ex
					);
				}
			} else {
				// 7일 초과 = 다음달부터 해지(빌링키 제거)
				try {
					Payment toSave = paid;
					// 방금 달 결제가 APPROVED 상태라면 다음 사이클 준비 상태로 리셋
					if (toSave.getPayStatus() == PayStatus.AUTO_BILLING_APPROVED) {
						toSave = toSave.resetForNextCycle();
					}
					// 빌링키 제거 + ABORTED 전이
					toSave = toSave.clearBillingKey();
					paymentRepository.save(toSave);

					historyRepository.save(
						PaymentHistory.create(
							toSave.getPaymentId(),
							toSave.getPayStatus(),
							"구독 해지 예약(다음 결제 미청구)"
						)
					);

					// 7일 초과 결제 취소 알림
					notificationProducer.cancelScheduled(memberId, orderId);

					return new PaymentCancelResponse(
						toSave.getPaymentId(),
						toSave.getPayStatus().name()
					);
				} catch (Exception ex) {
					log.error("구독 해지 예약 실패: orderId={}", orderId, ex);
					throw new PaymentApplicationException(ErrorCode.PAYMENT_CANCEL_ERROR, ex);
				}
			}
		}

		// 일회성 결제(구독 아님): 일단은 전액 환불 처리, 추후에 상태를 추가해서 멘토링 구매하고 게시판 들어가던가 했을때 상태 바꿔서 환불 안되게 해야할듯
		String paymentKey = paid.getPaymentKey();
		if (paymentKey == null || paymentKey.isBlank()) {
			log.error("취소 불가: paymentKey 없음 → orderId={}", orderId);
			throw new PaymentApplicationException(ErrorCode.PAYMENT_CANCEL_ERROR);
		}

		// [2/2] SAGA 결제 취소 호출
		log.info("[2/2] SAGA 결제 취소 호출 → paymentKey={}, orderId={}, cancelAmount={}, reason={}",
			paymentKey, orderId, cancelAmount, reason);
		try {
			PaymentCancelResponse res = paymentSaga.cancelWithCompensation(
				paymentKey, orderId, cancelAmount, reason
			);
			log.info("[2/2] SAGA 결제 취소 완료 → paymentKey={}, orderId={}",
				paymentKey, orderId);

			// 결제 취소 알림
			notificationProducer.cancelled(memberId, orderId, cancelAmount);


			return res;
		} catch (Exception ex) {
			log.error("결제 취소 실패: paymentKey={}, orderId={}, cancelAmount={}",
				paymentKey, orderId, cancelAmount, ex);
			throw new PaymentApplicationException(
				ErrorCode.PAYMENT_CANCEL_ERROR, ex
			);
		}
	}

	/**
	 * 빌링키 발급
	 */
	@Override
	@Transactional
	public PaymentIssueBillingKeyResponse issueBillingKey(
		Long memberId,
		PaymentIssueBillingKeyParam param
	) {
		log.info("[빌링키 발급 요청 시작] memberId={}, orderId={}, authKey={}, customerKey={}",
			memberId,
			param.getOrderId(),
			param.getAuthKey(),
			param.getCustomerKey()
		);

		// [1/2] 주문 조회 & 소유권 검증
		log.info("[1/2] 주문 조회 및 소유권 검증 → orderId={}", param.getOrderId());
		Payment paid = paymentRepository.findByOrderId(param.getOrderId())
			.orElseThrow(() -> new PaymentApplicationException(ErrorCode.ORDER_NOT_FOUND));
		paid.verifyOwnership(memberId);
		log.info("[1/2] 소유권 검증 완료 → memberId={} owns orderId={}", memberId, param.getOrderId());

		// [2/2] SAGA 빌링키 발급 호출
		log.info("[2/2] SAGA 빌링키 발급 호출 → orderId={}, authKey={}, customerKey={}",
			param.getOrderId(), param.getAuthKey(), param.getCustomerKey());
		try {
			PaymentIssueBillingKeyResponse res = paymentSaga.issueKeyWithCompensation(param);
			log.info("[2/2] SAGA 빌링키 발급 완료 → orderId={}, billingKey={}",
				param.getOrderId(), res.getBillingKey());

			// 자동결제 승인 알림
			notificationProducer.billingKeyIssued(memberId, param.getOrderId());

			return res;
		} catch (Exception ex) {
			log.error("빌링키 발급 실패: orderId={}", param.getOrderId(), ex);
			throw new PaymentApplicationException(
				ErrorCode.BILLING_ISSUE_ERROR, ex
			);
		}
	}

	/**
	 * 자동결제 승인
	 */
	@Override
	@Transactional
	public PaymentConfirmResponse chargeWithBillingKey(
		Long memberId,
		PaymentAutoChargeParam param,
		String idempotencyKey
	) {
		log.info("[자동결제 승인 요청 시작] memberId={}, orderId={}, billingKey={}, amount={}, customerKey={}",
			memberId,
			param.getOrderId(),
			param.getBillingKey(),
			param.getAmount(),
			param.getCustomerKey()
		);

		// [1/3] 주문 조회 & 소유권 검증
		log.info("[1/3] 주문 조회 및 소유권 검증 → orderId={}", param.getOrderId());
		Payment paid = paymentRepository.findByOrderId(param.getOrderId())
			.orElseThrow(() -> new PaymentApplicationException(ErrorCode.ORDER_NOT_FOUND));
		paid.verifyOwnership(memberId);
		log.info("[1/3] 소유권 검증 완료 → memberId={} owns orderId={}",
			memberId, param.getOrderId());

		// [2/3] SAGA 자동결제 호출
		log.info("[2/3] SAGA 자동결제 호출 → billingKey={}, orderId={}, amount={}, idemKey={}",
			param.getBillingKey(), param.getOrderId(), param.getAmount(), idempotencyKey);
		try {
			PaymentConfirmResponse res = paymentSaga.autoChargeWithCompensation(param, idempotencyKey);
			log.info("[2/3] SAGA 자동결제 완료 → paymentId={}", res.getPaymentId());

			// 자동결제 승인 알림
			notificationProducer.autoBillingApproved(memberId, param.getOrderId(), param.getAmount());

			// [3/3] 결과 반환
			log.info("[3/3] 자동결제 응답 반환 → paymentId={}", res.getPaymentId());
			return res;
		} catch (Exception ex) {
			log.error("자동결제 승인 실패: billingKey={}, orderId={}",
				param.getBillingKey(), param.getOrderId(), ex);
			throw new PaymentApplicationException(
				ErrorCode.AUTO_CHARGE_ERROR, ex
			);
		}
	}

	/**
	 * 미결제 주문 만료 메서드
	 */
	@Override
	@Transactional
	public void expireIfReady(Long memberId, String orderId) {
		Payment p = paymentRepository.findByOrderIdForUpdate(orderId)
			.orElseThrow(() -> new PaymentApplicationException(ErrorCode.ORDER_NOT_FOUND));
		p.verifyOwnership(memberId);

		if (p.getPayStatus() == PayStatus.READY) {
			Payment aborted = p.transitionTo(PayStatus.ABORTED);
			paymentRepository.save(aborted);
			historyRepository.save(PaymentHistory.create(
				aborted.getPaymentId(), aborted.getPayStatus(), "사용자 이탈로 주문 만료"
			));
			log.info("[주문 만료] orderId={} -> ABORTED", orderId);
		} else {
			log.info("[주문 만료 스킵] orderId={}, status={}", orderId, p.getPayStatus());
		}
	}

	/**
	 * 테스트용 빌링키 발급 상태 전이 메서드
	 */
	@Override
	@Transactional
	public void testTransitionToReady(String orderId, String billingKey) {
		log.info("[테스트용 빌링키 전이 요청 시작] orderId={}, billingKey={}",
			orderId, billingKey);

		// [1/2] 주문 조회
		log.info("[1/2] 주문 조회 → orderId={}", orderId);
		Payment payment = paymentRepository.findByOrderId(orderId)
			.orElseThrow(() -> new PaymentApplicationException(ErrorCode.ORDER_NOT_FOUND));

		// [2/2] 상태 전이 및 이력 기록
		log.info("[2/2] 상태 전이 및 이력 기록 → orderId={}, billingKey={}",
			orderId, billingKey);
		try {
			payment = payment.registerBillingKey(billingKey);
			paymentRepository.save(payment);
			historyRepository.save(
				PaymentHistory.create(
					payment.getPaymentId(),
					payment.getPayStatus(),
					"테스트용 빌링키 전이"
				)
			);
			log.info("[2/2] 전이 완료 → paymentId={}", payment.getPaymentId());
		} catch (Exception ex) {
			log.error("테스트용 빌링키 전이 실패: orderId={}, billingKey={}",
				orderId, billingKey, ex);
			throw new PaymentApplicationException(
				ErrorCode.TEST_READY_ERROR, ex
			);
		}
	}
}