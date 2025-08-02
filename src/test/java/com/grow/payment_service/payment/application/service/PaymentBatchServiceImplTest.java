package com.grow.payment_service.payment.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.service.impl.PaymentBatchServiceImpl;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.global.exception.PaymentApplicationException;
import com.grow.payment_service.payment.infra.paymentprovider.TossPaymentClient;
import com.grow.payment_service.payment.infra.redis.RedisIdempotencyAdapter;

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
	@Mock
	private RedisIdempotencyAdapter idempotencyAdapter;

	@InjectMocks
	PaymentBatchServiceImpl batchService;

	@Captor
	ArgumentCaptor<PaymentAutoChargeParam> autoChargeParamCaptor;
	@Captor
	ArgumentCaptor<Payment> paymentCaptor;
	@Captor
	ArgumentCaptor<PaymentHistory> historyCaptor;
	@Captor
	private ArgumentCaptor<String> idempotencyKeyCaptor;

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
		// 준비
		Payment ready = setupReadyPayment();
		given(paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_READY))
			.willReturn(List.of(ready));

		//  멱등키 생성 Stub: 유효한 UUID 문자열 반환
		String fakeUuid = "123e4567-e89b-12d3-a456-426614174000";
		given(idempotencyAdapter.getOrCreateKey(anyString()))
			.willReturn(fakeUuid);

		//  멱등키 예약 성공 Stub
		given(idempotencyAdapter.reserve(fakeUuid))
			.willReturn(true);

		// 자동결제 호출 스텁
		PaymentConfirmResponse mockRes =
			new PaymentConfirmResponse(42L, PayStatus.AUTO_BILLING_APPROVED.name());
		given(paymentService.chargeWithBillingKey(any(PaymentAutoChargeParam.class), anyString()))
			.willReturn(mockRes);

		// 실행
		batchService.processMonthlyAutoCharge();

		// 캡처 및 검증
		then(paymentService).should().chargeWithBillingKey(
			autoChargeParamCaptor.capture(),
			idempotencyKeyCaptor.capture()
		);
		String idemKey = idempotencyKeyCaptor.getValue();

		//  UUID 포맷 검증
		assertThatCode(() -> UUID.fromString(idemKey))
			.doesNotThrowAnyException();

		//  리턴된 키가 stub한 fakeUuid 와 동일함
		assertThat(idemKey).isEqualTo(fakeUuid);
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

	@Test
	void processSingleAutoCharge_successCycle() {
		// 준비: AUTO_BILLING_READY 상태의 결제
		Payment ready = setupReadyPayment();
		given(paymentRepository.findById(ready.getPaymentId()))
			.willReturn(java.util.Optional.of(ready));

		// 멱등키 생성·예약
		String fakeUuid = "123e4567-e89b-12d3-a456-426614174000";
		given(idempotencyAdapter.getOrCreateKey(anyString())).willReturn(fakeUuid);
		given(idempotencyAdapter.reserve(fakeUuid)).willReturn(true);

		// 외부 과금 성공 응답
		PaymentConfirmResponse mockRes =
			new PaymentConfirmResponse(100L, PayStatus.AUTO_BILLING_APPROVED.name());
		given(paymentService.chargeWithBillingKey(any(), eq(fakeUuid)))
			.willReturn(mockRes);

		// 실행
		batchService.processSingleAutoCharge(ready.getPaymentId());

		// 결제 상태 전이 저장: IN_PROGRESS, APPROVED, READY 총 3회
		then(paymentRepository).should(times(3)).save(paymentCaptor.capture());
		var saved = paymentCaptor.getAllValues();
		assertThat(saved.get(0).getPayStatus()).isEqualTo(PayStatus.AUTO_BILLING_IN_PROGRESS);
		assertThat(saved.get(1).getPayStatus()).isEqualTo(PayStatus.AUTO_BILLING_APPROVED);
		assertThat(saved.get(2).getPayStatus()).isEqualTo(PayStatus.AUTO_BILLING_READY);

		// 이력 저장도 3회
		then(historyRepository).should(times(3)).save(historyCaptor.capture());
		var histories = historyCaptor.getAllValues();
		assertThat(histories.get(0).getStatus()).isEqualTo(PayStatus.AUTO_BILLING_IN_PROGRESS);
		assertThat(histories.get(1).getStatus()).isEqualTo(PayStatus.AUTO_BILLING_APPROVED);
		assertThat(histories.get(2).getStatus()).isEqualTo(PayStatus.AUTO_BILLING_READY);
	}

	@Test
	void processSingleAutoCharge_whenReserveFails_thenSkipsProcessing() {
		Payment ready = setupReadyPayment();
		given(paymentRepository.findById(ready.getPaymentId()))
			.willReturn(java.util.Optional.of(ready));

		// 멱등키 예약 실패
		String fakeUuid = UUID.randomUUID().toString();
		given(idempotencyAdapter.getOrCreateKey(anyString())).willReturn(fakeUuid);
		given(idempotencyAdapter.reserve(fakeUuid)).willReturn(false);

		batchService.processSingleAutoCharge(ready.getPaymentId());

		// save, history, and external charge 호출되지 않아야 함
		then(paymentRepository).should(never()).save(any());
		then(historyRepository).shouldHaveNoInteractions();
		then(paymentService).shouldHaveNoInteractions();
	}

	@Test
	void processSingleAutoCharge_whenChargeThrows_thenThrowsApplicationException() {
		// 준비
		Payment ready = setupReadyPayment();
		given(paymentRepository.findById(ready.getPaymentId()))
			.willReturn(Optional.of(ready));
		String fakeUuid = UUID.randomUUID().toString();
		given(idempotencyAdapter.getOrCreateKey(anyString())).willReturn(fakeUuid);
		given(idempotencyAdapter.reserve(fakeUuid)).willReturn(true);

		// 외부 과금에서 예외 발생
		given(paymentService.chargeWithBillingKey(any(), eq(fakeUuid)))
			.willThrow(new RuntimeException("결제 시스템 오류"));

		// 실행 및 검증
		assertThatThrownBy(() -> batchService.processSingleAutoCharge(ready.getPaymentId()))
			.isInstanceOf(PaymentApplicationException.class)
			.satisfies(ex -> {
				// 원인이 RuntimeException("결제 시스템 오류") 인지 확인
				assertThat(ex.getCause())
					.isInstanceOf(RuntimeException.class)
					.hasMessageContaining("결제 시스템 오류");
			});

		// IN_PROGRESS 상태까지만 저장됐는지 확인
		then(paymentRepository).should().save(paymentCaptor.capture());
		assertThat(paymentCaptor.getValue().getPayStatus())
			.isEqualTo(PayStatus.AUTO_BILLING_IN_PROGRESS);
	}

	@Test
	void markAutoChargeFailedPermanently_transitionsAllInProgressToFailedAndClearsKeys() {
		// 준비: IN_PROGRESS 상태의 결제
		Payment base = setupReadyPayment();
		Payment inProgress = base.startAutoBilling();
		given(paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_IN_PROGRESS))
			.willReturn(List.of(inProgress));

		// save()는 입력 객체를 그대로 반환
		given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

		batchService.markAutoChargeFailedPermanently();

		// 실패 처리된 건이 save 됐는지 검증
		then(paymentRepository).should().save(paymentCaptor.capture());
		Payment cleared = paymentCaptor.getValue();
		assertThat(cleared.getPayStatus()).isEqualTo(PayStatus.AUTO_BILLING_FAILED);
		assertThat(cleared.getBillingKey()).isNull();

		// 이력 저장 검증
		then(historyRepository).should().save(historyCaptor.capture());
		PaymentHistory hist = historyCaptor.getValue();
		assertThat(hist.getStatus()).isEqualTo(PayStatus.AUTO_BILLING_FAILED);
		assertThat(hist.getReasonDetail()).isEqualTo("자동결제 재시도 한계 도달 -> 실패 처리 및 빌링키 제거");
	}
}