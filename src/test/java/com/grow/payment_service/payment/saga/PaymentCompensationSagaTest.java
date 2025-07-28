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
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.payment.infra.paymentprovider.TossBillingAuthResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossBillingChargeResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossCancelResponse;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;

import org.junit.jupiter.api.Test;
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
		when(gatewayPort.cancelPayment("key", CancelReason.USER_REQUEST.name(), 200, "사용자 요청 취소"))
			.thenReturn(mock(TossCancelResponse.class));
		when(persistenceService.savePaymentCancellation("key", CancelReason.USER_REQUEST, 200))
			.thenReturn(new PaymentCancelResponse(3L, "CANCELLED"));

		PaymentCancelResponse res = saga.cancelWithCompensation("key", "key", 200, CancelReason.USER_REQUEST);

		assertEquals(3L, res.getPaymentId());
		verify(gatewayPort).cancelPayment("key", CancelReason.USER_REQUEST.name(), 200, "사용자 요청 취소");
		verify(persistenceService).savePaymentCancellation("key", CancelReason.USER_REQUEST, 200);
	}

	// 4) cancelWithCompensation 실패 → recoverCancel 호출
	@Test
	void cancelWithCompensation_failure_triggersRecoverCancel() {
		when(gatewayPort.cancelPayment("key", CancelReason.USER_REQUEST.name(), 200, "사용자 요청 취소"))
			.thenReturn(mock(TossCancelResponse.class));
		doThrow(new QueryTimeoutException("DB timeout"))
			.when(persistenceService).savePaymentCancellation("key", CancelReason.USER_REQUEST, 200);

		IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> saga.cancelWithCompensation("key", "key", 200, CancelReason.USER_REQUEST)
		);
		assertTrue(ex.getMessage().contains("보상"));

		verify(gatewayPort).cancelPayment("key", CancelReason.USER_REQUEST.name(), 200, "사용자 요청 취소");
		verify(persistenceService, times(3)).savePaymentCancellation("key", CancelReason.USER_REQUEST, 200);
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
		var param = PaymentAutoChargeParam.builder()
			.billingKey("bkey").customerKey("ckey")
			.amount(500).orderId("oid")
			.orderName("order").customerEmail("e@mail")
			.customerName("name").taxFreeAmount(0).taxExemptionAmount(0)
			.build();

		TossBillingChargeResponse tossCharge = mock(TossBillingChargeResponse.class);
		when(gatewayPort.chargeWithBillingKey(any(), any(), anyInt(), any(), any(), any(), any(), anyInt(), anyInt()))
			.thenReturn(tossCharge);
		doThrow(new QueryTimeoutException("DB timeout"))
			.when(persistenceService).saveAutoChargeResult("oid", tossCharge);
		when(gatewayPort.cancelPayment("bkey", CancelReason.SYSTEM_ERROR.name(), 500, "자동결제 보상 취소"))
			.thenReturn(mock(TossCancelResponse.class));
		when(persistenceService.savePaymentCancellation("oid", CancelReason.SYSTEM_ERROR, 500))
			.thenReturn(new PaymentCancelResponse(2L, "CANCELLED"));

		IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> saga.autoChargeWithCompensation(param)
		);
		assertTrue(ex.getMessage().contains("자동결제 승인 보상(취소) 실패"));

		verify(gatewayPort).chargeWithBillingKey(any(), any(), anyInt(), any(), any(), any(), any(), anyInt(), anyInt());
		verify(gatewayPort).cancelPayment("bkey", CancelReason.SYSTEM_ERROR.name(), 500, "자동결제 보상 취소");
		verify(persistenceService).savePaymentCancellation("oid", CancelReason.SYSTEM_ERROR, 500);
	}
}