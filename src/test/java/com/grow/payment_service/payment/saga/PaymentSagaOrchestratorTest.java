package com.grow.payment_service.payment.saga;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.grow.payment_service.global.exception.PaymentSagaException;
import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingAuthResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingChargeResponse;
import com.grow.payment_service.payment.infra.redis.RedisIdempotencyAdapter;
import com.grow.payment_service.global.exception.ErrorCode;

@SpringBootTest(classes = PaymentSagaOrchestrator.class)
class PaymentSagaOrchestratorTest {

	@Autowired
	private PaymentSagaOrchestrator saga;

	@MockitoBean
	private PaymentGatewayPort gatewayPort;

	@MockitoBean
	private RetryablePersistenceService retryableService;

	@MockitoBean
	private RedisIdempotencyAdapter idempotencyAdapter;

	@MockitoBean
	private PaymentPersistenceService persistenceService;

	@Test
	@DisplayName("confirmWithCompensation: 성공 시 reserve → gateway → retryable → finish 호출")
	void confirmWithCompensation_success() {
		given(idempotencyAdapter.reserve("idem-key")).willReturn(true);
		willDoNothing().given(gatewayPort).confirmPayment("key", "order1", 1000);
		given(retryableService.saveConfirmation("key", "order1", 1000)).willReturn(42L);

		Long result = saga.confirmWithCompensation("key", "order1", 1000, "idem-key");

		assertThat(result).isEqualTo(42L);
		InOrder o = inOrder(idempotencyAdapter, gatewayPort, retryableService);
		o.verify(idempotencyAdapter).reserve("idem-key");
		o.verify(gatewayPort).confirmPayment("key", "order1", 1000);
		o.verify(retryableService).saveConfirmation("key", "order1", 1000);
		o.verify(idempotencyAdapter).finish("idem-key", "42");
		verify(idempotencyAdapter, never()).invalidate(anyString());
	}

	@Test
	@DisplayName("confirmWithCompensation: reserve=false and getResult!=null 이면 이전 결과 반환")
	void confirmWithCompensation_idempotentBranch() {
		given(idempotencyAdapter.reserve("idem-key")).willReturn(false);
		given(idempotencyAdapter.getResult("idem-key")).willReturn("77");

		Long result = saga.confirmWithCompensation("key", "order1", 1000, "idem-key");

		assertThat(result).isEqualTo(77L);
		verify(idempotencyAdapter).reserve("idem-key");
		verify(idempotencyAdapter).getResult("idem-key");
		verifyNoInteractions(gatewayPort, retryableService, persistenceService);
	}

	@Test
	@DisplayName("confirmWithCompensation: reserve=false and getResult==null 이면 in-flight 예외")
	void confirmWithCompensation_inFlight() {
		given(idempotencyAdapter.reserve("idem-key")).willReturn(false);
		given(idempotencyAdapter.getResult("idem-key")).willReturn(null);

		assertThatThrownBy(() ->
			saga.confirmWithCompensation("key", "order1", 1000, "idem-key")
		).isInstanceOf(PaymentSagaException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.IDEMPOTENCY_IN_FLIGHT);

		verify(idempotencyAdapter).reserve("idem-key");
		verify(idempotencyAdapter).getResult("idem-key");
		verifyNoInteractions(gatewayPort, retryableService);
	}

	@Test
	@DisplayName("confirmWithCompensation: 저장 실패 시 invalidate 후 예외 전파")
	void confirmWithCompensation_failure_propagates() {
		given(idempotencyAdapter.reserve("idem-key")).willReturn(true);
		willDoNothing().given(gatewayPort).confirmPayment("key", "order1", 1000);
		given(retryableService.saveConfirmation("key", "order1", 1000))
			.willThrow(new IllegalStateException("보상 실패"));

		assertThatThrownBy(() ->
			saga.confirmWithCompensation("key", "order1", 1000, "idem-key")
		).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("보상 실패");

		InOrder o = inOrder(idempotencyAdapter, gatewayPort, retryableService);
		o.verify(idempotencyAdapter).reserve("idem-key");
		o.verify(gatewayPort).confirmPayment("key", "order1", 1000);
		o.verify(retryableService).saveConfirmation("key", "order1", 1000);
		o.verify(idempotencyAdapter).invalidate("idem-key");
		verify(idempotencyAdapter, never()).finish(anyString(), anyString());
	}

	@Test
	@DisplayName("cancelWithCompensation: 정상 플로우")
	void cancelWithCompensation_success() {
		given(retryableService.saveCancelRequest("key", "order1", 200, CancelReason.USER_REQUEST))
			.willReturn(new PaymentCancelResponse(1L, "CANCEL_REQUESTED"));
		given(gatewayPort.cancelPayment("key", CancelReason.USER_REQUEST.name(), 200, "사용자 요청 취소"))
			.willReturn(null);
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

	@Test
	@DisplayName("issueKeyWithCompensation: 정상 플로우")
	void issueKeyWithCompensation_success() {
		TossBillingAuthResponse tossAuth = mock(TossBillingAuthResponse.class);
		given(tossAuth.getBillingKey()).willReturn("bk123");
		given(gatewayPort.issueBillingKey("a", "c")).willReturn(tossAuth);
		given(retryableService.saveBillingKey("order1", "bk123"))
			.willReturn(new PaymentIssueBillingKeyResponse("bk123"));

		PaymentIssueBillingKeyParam param = PaymentIssueBillingKeyParam.builder()
			.orderId("order1")
			.authKey("a")
			.customerKey("c")
			.build();

		PaymentIssueBillingKeyResponse res = saga.issueKeyWithCompensation(param);

		assertThat(res.getBillingKey()).isEqualTo("bk123");
		InOrder o = inOrder(gatewayPort, retryableService);
		o.verify(gatewayPort).issueBillingKey("a", "c");
		o.verify(retryableService).saveBillingKey("order1", "bk123");
	}

	@Test
	@DisplayName("autoChargeWithCompensation: 성공 시 reserve → gateway → retryable → finish 호출")
	void autoChargeWithCompensation_success() {
		given(idempotencyAdapter.reserve("idem-key")).willReturn(true);

		var param = PaymentAutoChargeParam.builder()
			.billingKey("bkey")
			.customerKey("ckey")
			.amount(500)
			.orderId("oid")
			.orderName("order")
			.customerEmail("e@mail")
			.customerName("name")
			.taxFreeAmount(0)          // tax 필드 포함
			.taxExemptionAmount(0)     // tax 필드 포함
			.build();

		TossBillingChargeResponse tossCharge = mock(TossBillingChargeResponse.class);
		// 9-arg 호출로 stub 설정
		given(gatewayPort.chargeWithBillingKey(
			eq("bkey"), eq("ckey"), eq(500),
			eq("oid"), eq("order"),
			eq("e@mail"), eq("name"),
			eq(0), eq(0)
		)).willReturn(tossCharge);

		given(retryableService.saveAutoCharge("bkey", "oid", 500, tossCharge))
			.willReturn(new PaymentConfirmResponse(99L, "DONE", "e@mail", "name"));

		PaymentConfirmResponse res = saga.autoChargeWithCompensation(param, "idem-key");

		assertThat(res.getPayStatus()).isEqualTo("DONE");
		assertThat(res.getCustomerEmail()).isEqualTo("e@mail");
		assertThat(res.getCustomerName()).isEqualTo("name");

		InOrder o = inOrder(idempotencyAdapter, gatewayPort, retryableService);
		o.verify(idempotencyAdapter).reserve("idem-key");
		// 9-arg verify
		o.verify(gatewayPort).chargeWithBillingKey(
			eq("bkey"), eq("ckey"), eq(500),
			eq("oid"), eq("order"),
			eq("e@mail"), eq("name"),
			eq(0), eq(0)
		);
		o.verify(retryableService).saveAutoCharge("bkey", "oid", 500, tossCharge);
		o.verify(idempotencyAdapter).finish("idem-key", "99");
	}

	@Test
	@DisplayName("autoChargeWithCompensation: reserve=false and getResult!=null 이면 이전 상태 반환")
	void autoChargeWithCompensation_idempotentBranch() {
		given(idempotencyAdapter.reserve("idem-key")).willReturn(false);
		given(idempotencyAdapter.getResult("idem-key")).willReturn("55");

		Payment existing = mock(Payment.class);
		given(existing.getPaymentId()).willReturn(55L);
		given(existing.getPayStatus()).willReturn(PayStatus.AUTO_BILLING_IN_PROGRESS);
		given(persistenceService.findByOrderId("oid")).willReturn(existing);

		var param = PaymentAutoChargeParam.builder()
			.billingKey("bkey")
			.customerKey("ckey")
			.amount(0)
			.orderId("oid")
			.orderName("order")
			.customerEmail("e@mail")
			.customerName("name")
			.build();

		PaymentConfirmResponse res = saga.autoChargeWithCompensation(param, "idem-key");
		assertThat(res.getPaymentId()).isEqualTo(55L);
		assertThat(res.getPayStatus()).isEqualTo("AUTO_BILLING_IN_PROGRESS");
		assertThat(res.getCustomerEmail()).isEqualTo("e@mail");
		assertThat(res.getCustomerName()).isEqualTo("name");

		verify(idempotencyAdapter).reserve("idem-key");
		verify(idempotencyAdapter).getResult("idem-key");
		verify(persistenceService).findByOrderId("oid");
		verifyNoInteractions(gatewayPort, retryableService);
	}
}