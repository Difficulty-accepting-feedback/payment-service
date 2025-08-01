package com.grow.payment_service.payment.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.List;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.service.impl.PaymentBatchServiceImpl;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.infra.redis.RedisIdempotencyAdapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentBatchServiceImplTest {

	@Mock
	PaymentRepository paymentRepository;
	@Mock
	PaymentHistoryRepository historyRepository;
	@Mock PaymentApplicationService paymentService;
	@Mock
	RedisIdempotencyAdapter idempotencyAdapter;

	@InjectMocks
	PaymentBatchServiceImpl batchService;

	@Captor ArgumentCaptor<PaymentAutoChargeParam> autoChargeParamCaptor;
	@Captor ArgumentCaptor<String> idempotencyKeyCaptor;
	@Captor ArgumentCaptor<Payment> paymentCaptor;
	@Captor ArgumentCaptor<PaymentHistory> historyCaptor;

	private Payment setupReadyPayment() {
		// READY → AUTO_BILLING_READY 상태로 전이된 테스트용 객체
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
		clearInvocations(paymentRepository, historyRepository, paymentService, idempotencyAdapter);
	}

	@Test
	void processMonthlyAutoCharge_whenNoTargets_thenNothingHappens() {
		given(paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_READY))
			.willReturn(List.of());

		batchService.processMonthlyAutoCharge();

		then(paymentService).shouldHaveNoInteractions();
		then(idempotencyAdapter).shouldHaveNoInteractions();
	}

	@Test
	void processMonthlyAutoCharge_withOneTarget_callsChargeWithBillingKey() {
		// 준비: 한 건 리턴
		Payment ready = setupReadyPayment();
		given(paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_READY))
			.willReturn(List.of(ready));

		// getOrCreateKey, reserve Stub
		given(idempotencyAdapter.getOrCreateKey(anyString()))
			.willReturn("idem-key-123");
		given(idempotencyAdapter.reserve("idem-key-123"))
			.willReturn(true);

		// 결제 서비스 Stub
		PaymentConfirmResponse mockRes =
			new PaymentConfirmResponse(42L, PayStatus.AUTO_BILLING_APPROVED.name());
		given(paymentService.chargeWithBillingKey(any(), anyString()))
			.willReturn(mockRes);

		// 상태 전이된 객체 저장 stub
		Payment inProgress = ready.startAutoBilling();
		given(paymentRepository.save(any(Payment.class)))
			.willReturn(inProgress);

		// 실행
		batchService.processMonthlyAutoCharge();

		// 상태 전이 저장 검증
		then(paymentRepository).should().save(paymentCaptor.capture());
		Payment saved = paymentCaptor.getValue();
		assertThat(saved.getPayStatus()).isEqualTo(PayStatus.AUTO_BILLING_IN_PROGRESS);

		// 파라미터 캡처
		then(paymentService).should().chargeWithBillingKey(
			autoChargeParamCaptor.capture(),
			idempotencyKeyCaptor.capture()
		);
		PaymentAutoChargeParam param = autoChargeParamCaptor.getValue();
		String idemKey = idempotencyKeyCaptor.getValue();

		assertThat(param.getBillingKey()).isEqualTo("BK-ABC");
		assertThat(param.getCustomerKey()).isEqualTo("cust_1");
		assertThat(param.getOrderId()).isEqualTo("order-1");
		assertThat(param.getAmount()).isEqualTo(1000);
		assertThat(idemKey).isEqualTo("idem-key-123");

		// 히스토리 저장 검증
		then(historyRepository).should().save(historyCaptor.capture());
		PaymentHistory hist = historyCaptor.getValue();
		assertThat(hist.getPaymentId()).isEqualTo(saved.getPaymentId());
		assertThat(hist.getStatus()).isEqualTo(PayStatus.AUTO_BILLING_IN_PROGRESS);
		assertThat(hist.getReasonDetail()).isEqualTo("자동결제 진행 중 상태로 전이");
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

		then(paymentRepository).should().save(paymentCaptor.capture());
		assertThat(paymentCaptor.getValue().getBillingKey()).isNull();

		then(historyRepository).should().save(historyCaptor.capture());
		PaymentHistory hist = historyCaptor.getValue();
		assertThat(hist.getPaymentId()).isEqualTo(ready.getPaymentId());
		assertThat(hist.getStatus()).isEqualTo(cleared.getPayStatus());
		assertThat(hist.getReasonDetail()).isEqualTo("빌링키 제거");
	}
}