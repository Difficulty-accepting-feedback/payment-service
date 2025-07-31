package com.grow.payment_service.payment.application.service.impl;

import java.util.List;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentBatchServiceImpl implements PaymentBatchService {

	private final PaymentRepository paymentRepository;
	private final PaymentHistoryRepository historyRepository;
	private final PaymentApplicationService paymentService;

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

		for (Payment p : targets) {
			try {
				// 멱등키로 orderId 사용
				String idempotencyKey = p.getOrderId();

				// 자동결제 파라미터 생성
				PaymentAutoChargeParam param = PaymentAutoChargeParam.builder()
					.billingKey(p.getBillingKey())
					.customerKey(p.getCustomerKey())
					.amount(p.getTotalAmount().intValue())
					.orderId(p.getOrderId())
					.orderName("GROW Plan #" + p.getOrderId())
					.customerEmail("member" + p.getMemberId() + "@example.com")
					.customerName("Member " + p.getMemberId())
					.taxFreeAmount(null)
					.taxExemptionAmount(null)
					.build();

				// idempotencyKey 함께 전달
				PaymentConfirmResponse res = paymentService.chargeWithBillingKey(param, idempotencyKey);

				log.info("[자동결제 성공] 결제ID={}, 주문ID={}, 결과상태={}",
					p.getPaymentId(), p.getOrderId(), res.getPayStatus());
			} catch (Exception ex) {
				log.error("[자동결제 실패] 결제ID={}, 주문ID={}, 원인={}",
					p.getPaymentId(), p.getOrderId(), ex.getMessage(), ex);
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
			if (p.getBillingKey() != null) {
				log.info("[빌링키 제거 시작] 결제ID={}, 기존BillingKey={}",
					p.getPaymentId(), p.getBillingKey());

				try {
					Payment updated = p.clearBillingKey();
					paymentRepository.save(updated);
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
		// (예시) AUTO_BILLING_READY → AUTO_BILLING_FAILED 로 전이
		List<Payment> targets = paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_READY);
		for (Payment p : targets) {
			Payment failed = p.failAutoBilling(FailureReason.RETRY_EXCEEDED);   // Domain 모델에 구현
			paymentRepository.save(failed);
			historyRepository.save(
				PaymentHistory.create(
					failed.getPaymentId(),
					failed.getPayStatus(),
					"자동 결제 실패 처리"
				)
			);
		}
		log.info("[자동결제] 5회 재시도 후 실패 상태 전이 완료: count={}", targets.size());
	}
}