package com.grow.payment_service.payment.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.PaymentApplicationException;
import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.infra.client.MemberClient;
import com.grow.payment_service.payment.infra.client.MemberInfoResponse;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.infra.redis.RedisIdempotencyAdapter;
import com.grow.payment_service.subscription.application.service.SubscriptionHistoryApplicationService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentBatchServiceImplTest {

	@Mock private PaymentRepository paymentRepository;
	@Mock private PaymentHistoryRepository historyRepository;
	@Mock private com.grow.payment_service.payment.application.service.PaymentApplicationService paymentService;
	@Mock private RedisIdempotencyAdapter idempotencyAdapter;
	@Mock private SubscriptionHistoryApplicationService subscriptionService;
	@Mock private MemberClient memberClient;

	@InjectMocks
	private PaymentBatchServiceImpl batchService;

	@Test
	@DisplayName("removeBillingKeysForMember: 대상 없음")
	void removeBillingKeysForMember_noTargets() {
		given(paymentRepository.findAllByMemberId(10L)).willReturn(Collections.emptyList());

		batchService.removeBillingKeysForMember(10L);

		then(historyRepository).shouldHaveNoInteractions();
		then(paymentRepository).should().findAllByMemberId(10L);
	}

	@Test
	@DisplayName("removeBillingKeysForMember: 성공 흐름")
	void removeBillingKeysForMember_success() {
		Payment p = Payment.of(
			2L, 20L, 200L, "ord-2", null,
			"bKey", "cust_20", 1000L,
			PayStatus.AUTO_BILLING_FAILED, "CARD",
			null, null
		);
		given(paymentRepository.findAllByMemberId(20L)).willReturn(List.of(p));

		batchService.removeBillingKeysForMember(20L);

		then(paymentRepository).should().save(argThat(updated ->
			updated.getBillingKey() == null &&
				updated.getPayStatus() == PayStatus.ABORTED
		));
		then(historyRepository).should()
			.save(any(PaymentHistory.class));
	}

	@Test
	@DisplayName("markAutoChargeFailedPermanently: 대상 없음")
	void markAutoChargeFailedPermanently_noTargets() {
		given(paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_IN_PROGRESS))
			.willReturn(Collections.emptyList());

		batchService.markAutoChargeFailedPermanently();

		then(paymentRepository).should().findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_IN_PROGRESS);
		then(historyRepository).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("markAutoChargeFailedPermanently: 성공 흐름")
	void markAutoChargeFailedPermanently_success() {
		Payment p = Payment.of(
			3L, 30L, 300L, "ord-3", null,
			"bKey", "cust_30", 1500L,
			PayStatus.AUTO_BILLING_IN_PROGRESS, "CARD",
			null, null
		);
		given(paymentRepository.findAllByPayStatusAndBillingKeyIsNotNull(PayStatus.AUTO_BILLING_IN_PROGRESS))
			.willReturn(List.of(p));

		batchService.markAutoChargeFailedPermanently();

		then(paymentRepository).should().save(argThat(cleared ->
			cleared.getBillingKey() == null &&
				cleared.getPayStatus() == PayStatus.ABORTED
		));
		then(historyRepository).should()
			.save(any(PaymentHistory.class));
	}

	@Test
	@DisplayName("processSingleAutoCharge: reserve 실패")
	void processSingleAutoCharge_reserveFails() {
		Payment p = Payment.of(
			4L, 40L, 400L, "ord-4", null,
			"bKey", "cust_40", 2000L,
			PayStatus.AUTO_BILLING_READY, "CARD",
			null, null
		);
		given(paymentRepository.findById(4L)).willReturn(Optional.of(p));
		given(idempotencyAdapter.getOrCreateKey(anyString())).willReturn("idem");
		given(idempotencyAdapter.reserve("idem")).willReturn(false);

		batchService.processSingleAutoCharge(4L);

		then(paymentService).shouldHaveNoInteractions();
		then(historyRepository).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("processSingleAutoCharge: 성공 흐름")
	void processSingleAutoCharge_success() {
		// 준비: payment 레코드
		Payment p = Payment.of(
			5L, 50L, 500L, "ord-5", null,
			"bKey", "cust_50", 2500L,
			PayStatus.AUTO_BILLING_READY, "CARD",
			null, null
		);
		given(paymentRepository.findById(5L)).willReturn(Optional.of(p));
		given(idempotencyAdapter.getOrCreateKey(anyString())).willReturn("idem");
		given(idempotencyAdapter.reserve("idem")).willReturn(true);

		// 준비: memberClient stub —> RsData 로 감싸서 반환
		MemberInfoResponse memberDto = new MemberInfoResponse(50L, "foo@ex.com", "FooNick");
		given(memberClient.getMyInfo(50L))
			.willReturn(new RsData<>("200", "OK", memberDto));

		// 준비: paymentService stub
		PaymentConfirmResponse confirmRes = new PaymentConfirmResponse(
			5L,
			PayStatus.AUTO_BILLING_APPROVED.name(),
			"foo@ex.com",
			"FooNick"
		);
		given(paymentService.chargeWithBillingKey(
			eq(50L),
			any(PaymentAutoChargeParam.class),
			eq("idem")
		)).willReturn(confirmRes);

		// 실행
		batchService.processSingleAutoCharge(5L);

		// 검증: state transitions 저장
		then(paymentRepository).should(times(3)).save(any(Payment.class));
		then(historyRepository).should(times(3)).save(any(PaymentHistory.class));

		// 검증: 실제 호출된 param 에 이메일·이름 반영
		ArgumentCaptor<PaymentAutoChargeParam> captor =
			ArgumentCaptor.forClass(PaymentAutoChargeParam.class);
		then(paymentService)
			.should()
			.chargeWithBillingKey(eq(50L), captor.capture(), eq("idem"));
		PaymentAutoChargeParam used = captor.getValue();
		assertEquals("foo@ex.com", used.getCustomerEmail());
		assertEquals("FooNick",    used.getCustomerName());
	}

	@Test
	@DisplayName("processSingleAutoCharge: 결제 정보 없음 → 예외")
	void processSingleAutoCharge_notFound() {
		given(paymentRepository.findById(6L)).willReturn(Optional.empty());

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> batchService.processSingleAutoCharge(6L)
		);
		assertEquals(ErrorCode.BATCH_AUTO_CHARGE_ERROR, ex.getErrorCode());
	}
}