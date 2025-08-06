package com.grow.payment_service.payment.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingChargeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.global.exception.PaymentApplicationException;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;

@ExtendWith(MockitoExtension.class)
class PaymentPersistenceServiceImplTest {

	@Mock
	PaymentRepository paymentRepository;
	@Mock PaymentHistoryRepository historyRepository;
	@InjectMocks PaymentPersistenceServiceImpl service;

	// helper: create a Payment in given state
	private Payment makePayment(PayStatus status) {
		return Payment.of(
			123L,  // paymentId
			1L,    // memberId
			1L,    // planId
			"ord-1",
			null,  // paymentKey
			null,  // billingKey
			"cust_1",
			500L,
			status,
			"CARD",
			null,
			null
		);
	}

	@Test
	@DisplayName("savePaymentConfirmation: 정상 흐름 → DONE으로 전이")
	void savePaymentConfirmation_success() {
		Payment before = makePayment(PayStatus.READY);
		given(paymentRepository.findByOrderId("ord-1")).willReturn(Optional.of(before));
		given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

		Long id = service.savePaymentConfirmation("ord-1");

		assertEquals(123L, id);
		// transitionTo(DONE) 이후 save 호출 및 history 저장
		then(paymentRepository).should().save(argThat(p -> p.getPayStatus() == PayStatus.DONE));
		then(historyRepository).should().save(any());
	}

	@Test
	@DisplayName("savePaymentConfirmation: 주문 미존재 → 예외")
	void savePaymentConfirmation_notFound() {
		given(paymentRepository.findByOrderId("ord-1")).willReturn(Optional.empty());
		assertThrows(PaymentApplicationException.class, () ->
			service.savePaymentConfirmation("ord-1")
		);
	}

	@Test
	@DisplayName("requestCancel: 이미 CANCEL_REQUESTED인 경우, save 호출 없이 바로 리턴")
	void requestCancel_alreadyRequested() {
		Payment already = makePayment(PayStatus.CANCEL_REQUESTED);
		given(paymentRepository.findByOrderIdForUpdate("ord-1"))
			.willReturn(Optional.of(already));

		PaymentCancelResponse resp = service.requestCancel("ord-1", CancelReason.USER_REQUEST, 100);

		assertEquals(PayStatus.CANCEL_REQUESTED.name(), resp.getStatus());
		then(paymentRepository).should(never()).save(any());
		then(historyRepository).should(never()).save(any());
	}

	@Test
	@DisplayName("requestCancel: 정상 흐름 → CANCEL_REQUESTED로 전이")
	void requestCancel_success() {
		Payment before = makePayment(PayStatus.READY);
		given(paymentRepository.findByOrderIdForUpdate("ord-1"))
			.willReturn(Optional.of(before));
		given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

		PaymentCancelResponse resp = service.requestCancel("ord-1", CancelReason.USER_REQUEST, 100);

		assertEquals(PayStatus.CANCEL_REQUESTED.name(), resp.getStatus());
		then(paymentRepository).should().save(argThat(p -> p.getPayStatus() == PayStatus.CANCEL_REQUESTED));
		then(historyRepository).should().save(any());
	}

	@Test
	@DisplayName("completeCancel: READY 상태인 경우, 무시하고 현재 상태 리턴")
	void completeCancel_notRequested() {
		Payment before = makePayment(PayStatus.READY);
		given(paymentRepository.findByOrderIdForUpdate("ord-1"))
			.willReturn(Optional.of(before));

		PaymentCancelResponse resp = service.completeCancel("ord-1");

		assertEquals(PayStatus.READY.name(), resp.getStatus());
		then(paymentRepository).should(never()).save(any());
		then(historyRepository).should(never()).save(any());
	}

	@Test
	@DisplayName("completeCancel: 정상 흐름 → CANCELLED로 전이")
	void completeCancel_success() {
		Payment before = makePayment(PayStatus.CANCEL_REQUESTED);
		given(paymentRepository.findByOrderIdForUpdate("ord-1"))
			.willReturn(Optional.of(before));
		given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

		PaymentCancelResponse resp = service.completeCancel("ord-1");

		assertEquals(PayStatus.CANCELLED.name(), resp.getStatus());
		then(paymentRepository).should().save(argThat(p -> p.getPayStatus() == PayStatus.CANCELLED));
		then(historyRepository).should().save(any());
	}

	@Test
	@DisplayName("saveBillingKeyRegistration: 정상 흐름 → AUTO_BILLING_READY로 전이")
	void saveBillingKeyRegistration_success() {
		Payment before = makePayment(PayStatus.READY);
		given(paymentRepository.findByOrderIdForUpdate("ord-1"))
			.willReturn(Optional.of(before));
		given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

		PaymentIssueBillingKeyResponse resp =
			service.saveBillingKeyRegistration("ord-1", "bkey-123");

		assertEquals("bkey-123", resp.getBillingKey());
		then(paymentRepository).should().save(argThat(p ->
			p.getPayStatus() == PayStatus.AUTO_BILLING_READY &&
				"bkey-123".equals(p.getBillingKey())
		));
		then(historyRepository).should().save(any());
	}

	@Test
	@DisplayName("saveAutoChargeResult: tossRes.status=‘DONE’ → AUTO_BILLING_APPROVED")
	void saveAutoChargeResult_done() {
		Payment before = makePayment(PayStatus.AUTO_BILLING_IN_PROGRESS);
		given(paymentRepository.findByOrderId("ord-1")).willReturn(Optional.of(before));
		given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
		TossBillingChargeResponse mockRes = mock(TossBillingChargeResponse.class);
		given(mockRes.getStatus()).willReturn("DONE");

		PaymentConfirmResponse resp = service.saveAutoChargeResult("ord-1", mockRes);

		assertEquals(PayStatus.AUTO_BILLING_APPROVED.name(), resp.getPayStatus());
		then(historyRepository).should().save(argThat(h ->
			h.getStatus() == PayStatus.AUTO_BILLING_APPROVED
		));
	}

	@Test
	@DisplayName("saveAutoChargeResult: tossRes.status≠‘DONE’ → AUTO_BILLING_FAILED")
	void saveAutoChargeResult_failed() {
		Payment before = makePayment(PayStatus.AUTO_BILLING_IN_PROGRESS);
		given(paymentRepository.findByOrderId("ord-1")).willReturn(Optional.of(before));
		given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
		TossBillingChargeResponse mockRes = mock(TossBillingChargeResponse.class);
		given(mockRes.getStatus()).willReturn("ERROR");

		PaymentConfirmResponse resp = service.saveAutoChargeResult("ord-1", mockRes);

		assertEquals(PayStatus.AUTO_BILLING_FAILED.name(), resp.getPayStatus());
		then(historyRepository).should().save(argThat(h ->
			h.getStatus() == PayStatus.AUTO_BILLING_FAILED &&
				h.getReasonDetail().contains("실패")
		));
	}
}