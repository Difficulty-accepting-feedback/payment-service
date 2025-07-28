package com.grow.payment_service.payment.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.List;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.infra.paymentprovider.TossPaymentClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class PaymentBatchServiceImplTest {

	@Mock
	PaymentRepository paymentRepository;
	@Mock
	PaymentHistoryRepository historyRepository;
	@Mock
	PaymentApplicationService paymentService;
	@Mock
	TossPaymentClient tossClient;

	@InjectMocks
	PaymentBatchServiceImpl batchService;

	@Captor ArgumentCaptor<PaymentAutoChargeParam> autoChargeParamCaptor;
	@Captor ArgumentCaptor<Payment> paymentCaptor;
	@Captor ArgumentCaptor<PaymentHistory> historyCaptor;

	private Payment setupReadyPayment() {
		// 기본 create 상태(READY)에서 AUTO_BILLING_READY 로 전이
		Payment p = Payment.create(
			1L, 1L, "order-1",
			null, null,
			"cust_1",
			1000L,
			"CARD"
		);
		return p.registerBillingKey("BK-ABC");
	}

	@BeforeEach
	void cleanMocks() {
		clearInvocations(paymentRepository, historyRepository, paymentService, tossClient);
	}

	@Test
	void processMonthlyAutoCharge_whenNoTargets_thenNothingHappens() {
		given(paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_READY))
			.willReturn(List.of());

		batchService.processMonthlyAutoCharge();

		then(paymentService).shouldHaveNoInteractions();
	}

	@Test
	void processMonthlyAutoCharge_withOneTarget_callsChargeWithBillingKey() {
		Payment ready = setupReadyPayment();
		given(paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_READY))
			.willReturn(List.of(ready));

		PaymentConfirmResponse mockRes = new PaymentConfirmResponse(42L, PayStatus.AUTO_BILLING_APPROVED.name());
		given(paymentService.chargeWithBillingKey(any())).willReturn(mockRes);

		batchService.processMonthlyAutoCharge();

		// chargeWithBillingKey가 정확한 파라미터로 호출됐는지 검증
		then(paymentService).should().chargeWithBillingKey(autoChargeParamCaptor.capture());
		PaymentAutoChargeParam param = autoChargeParamCaptor.getValue();

		assertThat(param.getBillingKey()).isEqualTo("BK-ABC");
		assertThat(param.getCustomerKey()).isEqualTo("cust_1");
		assertThat(param.getOrderId()).isEqualTo("order-1");
		assertThat(param.getAmount()).isEqualTo(1000);
	}

	@Test
	void removeBillingKeysForMember_whenNoPayments_thenNothingHappens() {
		long memberId = 99L;
		given(paymentRepository.findAllByMemberId(memberId)).willReturn(List.of());

		batchService.removeBillingKeysForMember(memberId);

		then(paymentRepository).should(never()).save(any());
		then(historyRepository).shouldHaveNoInteractions();
	}

	@Test
	void removeBillingKeysForMember_withBillingKey_removesKeyAndSavesHistory() {
		long memberId = 2L;
		// READY 상태로 billingKey 등록된 결제
		Payment ready = Payment.create(
			memberId, 1L, "order-2",
			null, "BK-XYZ",
			"cust_2",
			5000L,
			"CARD"
		).registerBillingKey("BK-XYZ");

		given(paymentRepository.findAllByMemberId(memberId))
			.willReturn(List.of(ready));

		Payment cleared = ready.clearBillingKey();
		given(paymentRepository.save(any(Payment.class))).willReturn(cleared);

		batchService.removeBillingKeysForMember(memberId);

		// 결제 저장 시 billingKey=null 인 Payment가 전달되었는지 캡처
		then(paymentRepository).should().save(paymentCaptor.capture());
		Payment saved = paymentCaptor.getValue();
		assertThat(saved.getBillingKey()).isNull();
		assertThat(saved.getPaymentId()).isEqualTo(ready.getPaymentId());

		// 히스토리 저장 시 해당 paymentId, status, reasonDetail 확인
		then(historyRepository).should().save(historyCaptor.capture());
		PaymentHistory hist = historyCaptor.getValue();
		assertThat(hist.getPaymentId()).isEqualTo(ready.getPaymentId());
		assertThat(hist.getStatus()).isEqualTo(saved.getPayStatus());
		assertThat(hist.getReasonDetail()).isEqualTo("빌링키 제거");
	}
}