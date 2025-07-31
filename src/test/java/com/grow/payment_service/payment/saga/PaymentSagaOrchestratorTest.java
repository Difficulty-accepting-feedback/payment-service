package com.grow.payment_service.payment.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
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
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingAuthResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingChargeResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossCancelResponse;
import com.grow.payment_service.payment.infra.redis.RedisIdempotencyAdapter;
import com.grow.payment_service.payment.saga.RetryablePersistenceService;

@SpringBootTest(classes = PaymentServiceApplication.class)
class PaymentSagaOrchestratorTest {

	@MockitoBean
	private PaymentGatewayPort gatewayPort;

	@MockitoBean
	private RetryablePersistenceService retryableService;

	@MockitoBean
	private RedisIdempotencyAdapter idempotencyAdapter;

	@MockitoBean
	private PaymentPersistenceService persistenceService;

	@Autowired
	private PaymentSagaOrchestrator saga;

	// 1) confirmWithCompensation 성공
	@Test
	void confirmWithCompensation_success() {
		// 멱등키 검사 통과
		given(idempotencyAdapter.reserve("idem-key")).willReturn(true);

		// 외부 승인 호출 목킹
		willDoNothing().given(gatewayPort).confirmPayment("key", "order1", 1000);
		// DB 저장 목킹
		given(retryableService.saveConfirmation("key", "order1", 1000)).willReturn(42L);

		Long id = saga.confirmWithCompensation("key", "order1", 1000, "idem-key");

		assertThat(id).isEqualTo(42L);

		InOrder o = inOrder(gatewayPort, retryableService);
		o.verify(gatewayPort).confirmPayment("key", "order1", 1000);
		o.verify(retryableService).saveConfirmation("key", "order1", 1000);
	}

	// 2) confirmWithCompensation 실패: saveConfirmation 예외 전파
	@Test
	void confirmWithCompensation_failure_propagates() {
		given(idempotencyAdapter.reserve("idem-key")).willReturn(true);
		willDoNothing().given(gatewayPort).confirmPayment("key", "order1", 1000);
		given(retryableService.saveConfirmation("key", "order1", 1000))
			.willThrow(new IllegalStateException("보상 실패"));

		assertThatThrownBy(() ->
			saga.confirmWithCompensation("key", "order1", 1000, "idem-key")
		)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("보상 실패");

		then(gatewayPort).should().confirmPayment("key", "order1", 1000);
		then(retryableService).should().saveConfirmation("key", "order1", 1000);
	}

	// 3) cancelWithCompensation 성공
	@Test
	void cancelWithCompensation_success() {
		given(retryableService.saveCancelRequest("key", "order1", 200, CancelReason.USER_REQUEST))
			.willReturn(new PaymentCancelResponse(1L, "CANCEL_REQUESTED"));
		given(gatewayPort.cancelPayment("key", CancelReason.USER_REQUEST.name(), 200, "사용자 요청 취소"))
			.willReturn(mock(TossCancelResponse.class));
		given(retryableService.saveCancelComplete("order1"))
			.willReturn(new PaymentCancelResponse(3L, "CANCELLED"));

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
		given(gatewayPort.issueBillingKey("a", "c")).willReturn(tossAuth);
		given(tossAuth.getBillingKey()).willReturn("bk123");
		given(retryableService.saveBillingKey("order1", "bk123"))
			.willReturn(new PaymentIssueBillingKeyResponse("bk123"));

		PaymentIssueBillingKeyResponse res = saga.issueKeyWithCompensation(param);

		assertThat(res.getBillingKey()).isEqualTo("bk123");

		InOrder o = inOrder(gatewayPort, retryableService);
		o.verify(gatewayPort).issueBillingKey("a", "c");
		o.verify(retryableService).saveBillingKey("order1", "bk123");
	}

	// 5) autoChargeWithCompensation 성공
	@Test
	void autoChargeWithCompensation_success() {
		// 멱등키 검사 통과
		given(idempotencyAdapter.reserve("idem-key")).willReturn(true);

		var param = PaymentAutoChargeParam.builder()
			.billingKey("bkey").customerKey("ckey")
			.amount(500).orderId("oid")
			.orderName("order").customerEmail("e@mail")
			.customerName("name").taxFreeAmount(0).taxExemptionAmount(0)
			.build();

		TossBillingChargeResponse tossCharge = mock(TossBillingChargeResponse.class);
		given(gatewayPort.chargeWithBillingKey(
			eq("bkey"), eq("ckey"), eq(500), eq("oid"),
			eq("order"), eq("e@mail"), eq("name"), eq(0), eq(0)
		)).willReturn(tossCharge);
		given(retryableService.saveAutoCharge("bkey", "oid", 500, tossCharge))
			.willReturn(new PaymentConfirmResponse(99L, "DONE"));

		PaymentConfirmResponse res = saga.autoChargeWithCompensation(param, "idem-key");

		assertThat(res.getPayStatus()).isEqualTo("DONE");

		InOrder o = inOrder(gatewayPort, retryableService);
		o.verify(gatewayPort).chargeWithBillingKey(
			eq("bkey"), eq("ckey"), eq(500), eq("oid"),
			eq("order"), eq("e@mail"), eq("name"), eq(0), eq(0)
		);
		o.verify(retryableService).saveAutoCharge("bkey", "oid", 500, tossCharge);
	}
}