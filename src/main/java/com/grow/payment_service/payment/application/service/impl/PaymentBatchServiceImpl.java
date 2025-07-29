package com.grow.payment_service.payment.application.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.service.PaymentApplicationService;
import com.grow.payment_service.payment.application.service.PaymentBatchService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
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
	private final TossPaymentClient tossClient;

	@Override
	public void processMonthlyAutoCharge() {
		List<Payment> targets =
			paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_READY);

		if (targets.isEmpty()) {
			log.info("[자동결제 대상 없음] 처리할 결제 건이 없습니다.");
			return;
		}

		for (Payment p : targets) {
			try {
				PaymentConfirmResponse res = paymentService.chargeWithBillingKey(
					PaymentAutoChargeParam.builder()
						.billingKey(p.getBillingKey())
						.customerKey(p.getCustomerKey())
						.amount(p.getTotalAmount().intValue())
						.orderId(p.getOrderId())
						.orderName("GROW Plan #" + p.getOrderId())
						.customerEmail("member" + p.getMemberId() + "@example.com")
						.customerName("Member " + p.getMemberId())
						.taxFreeAmount(null)
						.taxExemptionAmount(null)
						.build()
				);
				log.info("[자동결제 성공] 결제ID={}, 주문ID={}, 결과상태={}",
					p.getPaymentId(), p.getOrderId(), res.getPayStatus());
			} catch (Exception ex) {
				log.error("[자동결제 실패] 결제ID={}, 주문ID={}, 원인={}",
					p.getPaymentId(), p.getOrderId(), ex.getMessage(), ex);
			}
		}
	}

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
			}
		}
	}
}