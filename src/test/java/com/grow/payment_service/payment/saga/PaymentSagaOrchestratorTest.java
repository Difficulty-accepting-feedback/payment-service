package com.grow.payment_service.payment.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.grow.payment_service.PaymentServiceApplication;
import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossBillingChargeResponse;
import com.grow.payment_service.payment.infra.paymentprovider.TossCancelResponse;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.payment.infra.paymentprovider.TossBillingAuthResponse;


@SpringBootTest(classes = PaymentServiceApplication.class)
class PaymentSagaOrchestratorTest {

	@MockitoBean
	private PaymentGatewayPort gatewayPort;

	@MockitoBean
	private RetryablePersistenceService retryableService;

	@Autowired
	private PaymentSagaOrchestrator saga;

	// 1) confirmWithCompensation 성공
	@Test
	void confirmWithCompensation_success() {
		// external
		doNothing().when(gatewayPort).confirmPayment("key", "order1", 1000);
		// persistence
		when(retryableService.saveConfirmation("key", "order1", 1000)).thenReturn(42L);

		Long id = saga.confirmWithCompensation("key", "order1", 1000);

		assertThat(id).isEqualTo(42L);
		InOrder o = inOrder(gatewayPort, retryableService);
		o.verify(gatewayPort).confirmPayment("key", "order1", 1000);
		o.verify(retryableService).saveConfirmation("key", "order1", 1000);
	}

	// 2) confirmWithCompensation 실패: retryableService.saveConfirmation 에서 예외
	@Test
	void confirmWithCompensation_failure_propagates() {
		doNothing().when(gatewayPort).confirmPayment("key", "order1", 1000);
		when(retryableService.saveConfirmation("key", "order1", 1000))
			.thenThrow(new IllegalStateException("보상 실패"));

		assertThatThrownBy(() ->
			saga.confirmWithCompensation("key", "order1", 1000)
		).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("보상 실패");

		verify(gatewayPort).confirmPayment("key", "order1", 1000);
		verify(retryableService).saveConfirmation("key", "order1", 1000);
	}

	// 3) cancelWithCompensation 성공
	@Test
	void cancelWithCompensation_success() {
		when(retryableService.saveCancelRequest("key", "order1", 200, CancelReason.USER_REQUEST))
			.thenReturn(new PaymentCancelResponse(1L, "CANCEL_REQUESTED"));
		when(gatewayPort.cancelPayment("key", CancelReason.USER_REQUEST.name(), 200, "사용자 요청 취소"))
			.thenReturn(mock(TossCancelResponse.class));
		when(retryableService.saveCancelComplete("order1"))
			.thenReturn(new PaymentCancelResponse(3L, "CANCELLED"));

		PaymentCancelResponse res = saga.cancelWithCompensation(
			"key", "order1", 200, CancelReason.USER_REQUEST
		);

		assertThat(res.getPaymentId()).isEqualTo(3L);

		InOrder o = inOrder(retryableService, gatewayPort);
		o.verify(retryableService).saveCancelRequest("key", "order1", 200, CancelReason.USER_REQUEST);
		o.verify(gatewayPort).cancelPayment("key", CancelReason.USER_REQUEST.name(), 200, "사용자 요청 취소");
		o.verify(retryableService).saveCancelComplete("order1");
	}

	// 4) issueKeyWithCompensation 성공
	@Test
	void issueKeyWithCompensation_success() {
		var param = PaymentIssueBillingKeyParam.builder()
			.orderId("order1").authKey("a").customerKey("c").build();
		TossBillingAuthResponse tossAuth = mock(TossBillingAuthResponse.class);
		when(gatewayPort.issueBillingKey("a", "c")).thenReturn(tossAuth);
		when(tossAuth.getBillingKey()).thenReturn("bk123");
		when(retryableService.saveBillingKey("order1", "bk123"))
			.thenReturn(new PaymentIssueBillingKeyResponse("bk123"));

		PaymentIssueBillingKeyResponse res = saga.issueKeyWithCompensation(param);

		assertThat(res.getBillingKey()).isEqualTo("bk123");
		InOrder o = inOrder(gatewayPort, retryableService);
		o.verify(gatewayPort).issueBillingKey("a", "c");
		o.verify(retryableService).saveBillingKey("order1", "bk123");
	}

	// 5) autoChargeWithCompensation 성공
	@Test
	void autoChargeWithCompensation_success() {
		var param = PaymentAutoChargeParam.builder()
			.billingKey("bkey").customerKey("ckey")
			.amount(500).orderId("oid")
			.orderName("order").customerEmail("e@mail")
			.customerName("name").taxFreeAmount(0).taxExemptionAmount(0)
			.build();

		TossBillingChargeResponse tossCharge = mock(TossBillingChargeResponse.class);
		var ok = new PaymentConfirmResponse(99L, "DONE");
		when(gatewayPort.chargeWithBillingKey(
			eq("bkey"), eq("ckey"), eq(500), eq("oid"),
			eq("order"), eq("e@mail"), eq("name"), eq(0), eq(0)
		)).thenReturn(tossCharge);
		when(retryableService.saveAutoCharge("bkey", "oid", 500, tossCharge)).thenReturn(ok);

		PaymentConfirmResponse res = saga.autoChargeWithCompensation(param);

		assertThat(res).isSameAs(ok);
		InOrder o = inOrder(gatewayPort, retryableService);
		o.verify(gatewayPort).chargeWithBillingKey(
			eq("bkey"), eq("ckey"), eq(500), eq("oid"),
			eq("order"), eq("e@mail"), eq("name"), eq(0), eq(0)
		);
		o.verify(retryableService).saveAutoCharge("bkey", "oid", 500, tossCharge);
	}
}