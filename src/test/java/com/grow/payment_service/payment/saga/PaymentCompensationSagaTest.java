package com.grow.payment_service.payment.saga;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.payment.infra.paymentprovider.TossBillingAuthResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossBillingChargeResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossCancelResponse;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class PaymentCompensationSagaTest {

	@MockitoBean
	private PaymentGatewayPort gatewayPort;

	@MockitoBean
	private PaymentPersistenceService persistenceService;

	@Autowired
	private PaymentCompensationSaga saga;

	// 1) confirmWithCompensation 성공
	@Test
	void confirmWithCompensation_success() {
		doNothing().when(gatewayPort).confirmPayment("key", "order1", 1000);
		when(persistenceService.savePaymentConfirmation("order1")).thenReturn(42L);

		Long id = saga.confirmWithCompensation("key", "order1", 1000);

		assertEquals(42L, id);
		verify(gatewayPort).confirmPayment("key", "order1", 1000);
		verify(persistenceService).savePaymentConfirmation("order1");
	}

	// 2) confirmWithCompensation 실패 → recoverConfirm 호출
	@Test
	void confirmWithCompensation_failure_triggersRecoverConfirm() {
		doNothing().when(gatewayPort).confirmPayment("key", "order1", 1000);

		// savePaymentConfirmation 실패로 fallbackMethod(recoverConfirm) 진입 유도
		doThrow(new QueryTimeoutException("DB timeout"))
			.when(persistenceService).savePaymentConfirmation("order1");

		// 보상 로직을 위한 mock 설정
		when(gatewayPort.cancelPayment("key", CancelReason.SYSTEM_ERROR.name(), 1000, "보상 취소"))
			.thenReturn(mock(TossCancelResponse.class));

		// recoverConfirm 내부 동작에 필요한 mock 설정
		var mockPayment = mock(Payment.class);
		when(persistenceService.findByOrderId("order1")).thenReturn(mockPayment);
		when(mockPayment.forceCancel(CancelReason.SYSTEM_ERROR)).thenReturn(mockPayment);
		when(mockPayment.forceCancel(CancelReason.SYSTEM_ERROR)).thenReturn(mockPayment);

		// saveHistory 호출 무시 (void 반환일 경우 doNothing 생략 가능)
		doNothing().when(persistenceService).saveHistory(anyLong(), any(), anyString());

		IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> saga.confirmWithCompensation("key", "order1", 1000)
		);
		assertTrue(ex.getMessage().contains("보상"));

		verify(gatewayPort).confirmPayment("key", "order1", 1000);
		verify(gatewayPort).cancelPayment("key", CancelReason.SYSTEM_ERROR.name(), 1000, "보상 취소");
		verify(persistenceService).findByOrderId("order1");
		verify(mockPayment).forceCancel(CancelReason.SYSTEM_ERROR);
		verify(persistenceService).saveForceCancelledPayment(mockPayment);
		verify(persistenceService).saveHistory(anyLong(), any(), anyString());
	}

	// 3) cancelWithCompensation 성공
	@Test
	void cancelWithCompensation_success() {
		// 1) 외부 호출 목킹
		when(gatewayPort.cancelPayment(
			"key", CancelReason.USER_REQUEST.name(), 200, "사용자 요청 취소"
		)).thenReturn(mock(TossCancelResponse.class));

		// 2) DB: requestCancel 목킹
		when(persistenceService.requestCancel("order1", CancelReason.USER_REQUEST, 200))
			.thenReturn(new PaymentCancelResponse(1L, "CANCEL_REQUESTED"));
		// 3) DB: completeCancel 목킹
		when(persistenceService.completeCancel("order1"))
			.thenReturn(new PaymentCancelResponse(3L, "CANCELLED"));

		// 실제 호출
		PaymentCancelResponse res = saga.cancelWithCompensation(
			"key", "order1", 200, CancelReason.USER_REQUEST
		);

		// 결과 검증
		assertEquals(3L, res.getPaymentId());

		// 호출 순서 및 횟수 검증
		InOrder inOrder = inOrder(persistenceService, gatewayPort);
		inOrder.verify(persistenceService).requestCancel("order1", CancelReason.USER_REQUEST, 200);
		inOrder.verify(gatewayPort).cancelPayment(
			"key", CancelReason.USER_REQUEST.name(), 200, "사용자 요청 취소"
		);
		inOrder.verify(persistenceService).completeCancel("order1");
	}

	// 4) cancelWithCompensation 실패 → recoverCancelRequest 호출
	@Test
	void cancelWithCompensation_failure_onRequestPhase() {
		// given: saveCancelRequest 단계에서 타임아웃 발생
		doThrow(new QueryTimeoutException("DB timeout"))
			.when(persistenceService)
			.requestCancel("order1", CancelReason.USER_REQUEST, 200);

		// given: recoverCancelRequest 내부 호출 목킹
		var mockPayment = mock(Payment.class);
		when(persistenceService.findByOrderId("order1")).thenReturn(mockPayment);
		when(mockPayment.forceCancel(CancelReason.SYSTEM_ERROR)).thenReturn(mockPayment);
		when(mockPayment.getPaymentId()).thenReturn(2L);
		when(mockPayment.getPayStatus()).thenReturn(PayStatus.CANCELLED);
		doNothing().when(persistenceService).saveForceCancelledPayment(mockPayment);
		doNothing().when(persistenceService).saveHistory(anyLong(), any(), anyString());

		// given: 외부 API 호출 목킹
		when(gatewayPort.cancelPayment(
			"key", CancelReason.USER_REQUEST.name(), 200, "사용자 요청 취소"
		)).thenReturn(mock(TossCancelResponse.class));

		// given: completeCancel 단계 목킹
		when(persistenceService.completeCancel("order1"))
			.thenReturn(new PaymentCancelResponse(2L, "CANCELLED"));

		// when
		PaymentCancelResponse res = saga.cancelWithCompensation(
			"key", "order1", 200, CancelReason.USER_REQUEST
		);

		// then: 최종 COMPLETE 결과 검증
		assertEquals(2L, res.getPaymentId());
		assertEquals("CANCELLED", res.getStatus());

		// verify: requestCancel는 retry 3회, 그 이후 recover → external → complete 순서
		InOrder inOrder = inOrder(persistenceService, gatewayPort, mockPayment);
		inOrder.verify(persistenceService, times(3))
			.requestCancel("order1", CancelReason.USER_REQUEST, 200);
		inOrder.verify(persistenceService)
			.findByOrderId("order1");
		inOrder.verify(mockPayment)
			.forceCancel(CancelReason.SYSTEM_ERROR);
		inOrder.verify(persistenceService)
			.saveForceCancelledPayment(mockPayment);
		inOrder.verify(persistenceService)
			.saveHistory(2L, PayStatus.CANCELLED, "보상 트랜잭션(취소 요청 저장 실패)에 의한 강제 상태 전이");
		inOrder.verify(gatewayPort)
			.cancelPayment("key", CancelReason.USER_REQUEST.name(), 200, "사용자 요청 취소");
		inOrder.verify(persistenceService)
			.completeCancel("order1");
	}
	// 5) issueKeyWithCompensation 성공
	@Test
	void issueKeyWithCompensation_success() {
		var param = PaymentIssueBillingKeyParam.builder()
			.orderId("order1").authKey("a").customerKey("c").build();
		TossBillingAuthResponse tossAuth = mock(TossBillingAuthResponse.class);
		when(gatewayPort.issueBillingKey("a", "c")).thenReturn(tossAuth);
		when(tossAuth.getBillingKey()).thenReturn("bk123");
		when(persistenceService.saveBillingKeyRegistration("order1", "bk123"))
			.thenReturn(new PaymentIssueBillingKeyResponse("bk123"));

		PaymentIssueBillingKeyResponse res = saga.issueKeyWithCompensation(param);

		assertEquals("bk123", res.getBillingKey());
		verify(gatewayPort).issueBillingKey("a", "c");
		verify(persistenceService).saveBillingKeyRegistration("order1", "bk123");
	}

	// 6) issueKeyWithCompensation 실패 → recoverIssueKey 호출
	@Test
	void issueKeyWithCompensation_failure_triggersRecoverIssueKey() {
		var param = PaymentIssueBillingKeyParam.builder()
			.orderId("order1").authKey("a").customerKey("c").build();
		TossBillingAuthResponse tossAuth = mock(TossBillingAuthResponse.class);
		when(gatewayPort.issueBillingKey("a", "c")).thenReturn(tossAuth);
		when(tossAuth.getBillingKey()).thenReturn("bk123");
		doThrow(new QueryTimeoutException("DB timeout"))
			.when(persistenceService).saveBillingKeyRegistration("order1", "bk123");

		IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> saga.issueKeyWithCompensation(param)
		);
		assertTrue(ex.getMessage().contains("빌링키 발급 DB저장 재시도 실패"));

		verify(gatewayPort).issueBillingKey("a", "c");
		verify(persistenceService, times(3)).saveBillingKeyRegistration("order1", "bk123");
	}

	// 7) autoChargeWithCompensation 성공
	@Test
	void autoChargeWithCompensation_success() {
		var param = PaymentAutoChargeParam.builder()
			.billingKey("bkey").customerKey("ckey")
			.amount(500).orderId("oid")
			.orderName("order").customerEmail("e@mail")
			.customerName("name").taxFreeAmount(0).taxExemptionAmount(0)
			.build();

		TossBillingChargeResponse tossCharge = mock(TossBillingChargeResponse.class);
		PaymentConfirmResponse ok = new PaymentConfirmResponse(99L, "DONE");
		when(gatewayPort.chargeWithBillingKey(
			eq("bkey"), eq("ckey"), eq(500), eq("oid"),
			anyString(), anyString(), anyString(), anyInt(), anyInt()
		)).thenReturn(tossCharge);
		when(persistenceService.saveAutoChargeResult("oid", tossCharge)).thenReturn(ok);

		PaymentConfirmResponse res = saga.autoChargeWithCompensation(param);

		assertSame(ok, res);
		verify(gatewayPort).chargeWithBillingKey(
			eq("bkey"), eq("ckey"), eq(500), eq("oid"),
			eq("order"), eq("e@mail"), eq("name"), eq(0), eq(0)
		);
		verify(persistenceService).saveAutoChargeResult("oid", tossCharge);
	}

	// 8) autoChargeWithCompensation 실패 → recoverAutoCharge 호출
	@Test
	void autoChargeWithCompensation_failure_triggersRecoverAutoCharge() {
		// given
		var param = PaymentAutoChargeParam.builder()
			.billingKey("bkey").customerKey("ckey")
			.amount(500).orderId("oid")
			.orderName("order").customerEmail("e@mail")
			.customerName("name").taxFreeAmount(0).taxExemptionAmount(0)
			.build();

		TossBillingChargeResponse tossCharge = mock(TossBillingChargeResponse.class);
		when(gatewayPort.chargeWithBillingKey(
			any(), any(), anyInt(), any(), any(), any(), any(), anyInt(), anyInt()
		)).thenReturn(tossCharge);

		// 1) DB 저장 실패 유도
		doThrow(new QueryTimeoutException("DB timeout"))
			.when(persistenceService).saveAutoChargeResult("oid", tossCharge);

		// 2) Compensation 호출 스텁
		when(gatewayPort.cancelPayment(
			"bkey", CancelReason.SYSTEM_ERROR.name(), 500, "자동결제 보상 취소"
		)).thenReturn(mock(TossCancelResponse.class));
		when(persistenceService.requestCancel("oid", CancelReason.SYSTEM_ERROR, 500))
			.thenReturn(new PaymentCancelResponse(1L, "CANCEL_REQUESTED"));
		when(persistenceService.completeCancel("oid"))
			.thenReturn(new PaymentCancelResponse(2L, "CANCELLED"));

		// when & then
		IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> saga.autoChargeWithCompensation(param)
		);
		assertTrue(ex.getMessage().contains("자동결제 승인 보상(취소) 실패"));

		// verify
		verify(gatewayPort).chargeWithBillingKey(
			any(), any(), anyInt(), any(), any(), any(), any(), anyInt(), anyInt()
		);
		verify(gatewayPort).cancelPayment(
			"bkey", CancelReason.SYSTEM_ERROR.name(), 500, "자동결제 보상 취소"
		);
		verify(persistenceService).requestCancel("oid", CancelReason.SYSTEM_ERROR, 500);
		verify(persistenceService).completeCancel("oid");
	}
}