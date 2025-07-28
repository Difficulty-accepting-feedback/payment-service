// src/test/java/com/grow/payment_service/payment/saga/PaymentCompensationSagaTest.java
package com.grow.payment_service.payment.saga;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.service.PaymentApplicationService;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest()
class PaymentCompensationSagaTest {

	@MockitoBean
	private PaymentApplicationService paymentService;

	@Autowired
	private PaymentCompensationSaga saga;

	// 1) confirmWithCompensation 성공
	@Test
	void confirmWithCompensation_success() {
		when(paymentService.confirmPayment("key", "order1", 1000))
			.thenReturn(42L);

		Long id = saga.confirmWithCompensation("key", "order1", 1000);

		assertEquals(42L, id);
		verify(paymentService).confirmPayment("key", "order1", 1000);
		verify(paymentService, never())
			.cancelPayment(anyString(), anyString(), anyInt(), any());
	}

	// 2) confirmWithCompensation 실패 → recoverConfirm 호출되어 보상 취소
	@Test
	void confirmWithCompensation_failure_triggersRecoverConfirm() {
		when(paymentService.confirmPayment("key", "order1", 1000))
			.thenThrow(new RuntimeException("DB error"));
		when(paymentService.cancelPayment("key", "order1", 1000, CancelReason.SYSTEM_ERROR))
			.thenReturn(new PaymentCancelResponse(1L, "CANCELLED"));

		IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> saga.confirmWithCompensation("key", "order1", 1000)
		);
		assertTrue(ex.getMessage().contains("결제 승인 재시도 -> 취소 처리되었습니다."));

		verify(paymentService).confirmPayment("key", "order1", 1000);
		verify(paymentService).cancelPayment("key", "order1", 1000, CancelReason.SYSTEM_ERROR);
	}

	// 3) issueKeyWithCompensation 성공
	@Test
	void issueKeyWithCompensation_success() {
		var param = PaymentIssueBillingKeyParam.builder()
			.orderId("order1")
			.authKey("auth")
			.customerKey("cust")
			.build();
		when(paymentService.issueBillingKey(param))
			.thenReturn(new com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse("bk123"));

		String bk = saga.issueKeyWithCompensation(param);

		assertEquals("bk123", bk);
		verify(paymentService).issueBillingKey(param);
	}

	// 4) issueKeyWithCompensation 실패 → recoverIssueKey 호출
	@Test
	void issueKeyWithCompensation_failure_triggersRecoverIssueKey() {
		var param = PaymentIssueBillingKeyParam.builder()
			.orderId("order1")
			.authKey("auth")
			.customerKey("cust")
			.build();
		when(paymentService.issueBillingKey(param))
			.thenThrow(new RuntimeException("DB error"));

		IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> saga.issueKeyWithCompensation(param)
		);
		assertTrue(ex.getMessage().contains("빌링키 발급 재시도 실패"));
		verify(paymentService).issueBillingKey(param);
	}

	// 5) autoChargeWithCompensation 성공
	@Test
	void autoChargeWithCompensation_success() {
		var param = PaymentAutoChargeParam.builder()
			.billingKey("bkey")
			.customerKey("ckey")
			.amount(500)
			.orderId("order2")
			.orderName("name")
			.customerEmail("e@mail")
			.customerName("name")
			.taxFreeAmount(null)
			.taxExemptionAmount(null)
			.build();
		var resp = new PaymentConfirmResponse(99L, "AUTO_BILLING_APPROVED");
		when(paymentService.chargeWithBillingKey(param)).thenReturn(resp);

		PaymentConfirmResponse out = saga.autoChargeWithCompensation(param);

		assertSame(resp, out);
		verify(paymentService).chargeWithBillingKey(param);
	}

	// 6) autoChargeWithCompensation 실패 → recoverAutoCharge 호출
	@Test
	void autoChargeWithCompensation_failure_triggersRecoverAutoCharge() {
		var param = PaymentAutoChargeParam.builder()
			.billingKey("bkey")
			.customerKey("ckey")
			.amount(500)
			.orderId("order2")
			.orderName("name")
			.customerEmail("e@mail")
			.customerName("name")
			.taxFreeAmount(null)
			.taxExemptionAmount(null)
			.build();
		when(paymentService.chargeWithBillingKey(param))
			.thenThrow(new RuntimeException("DB error"));
		when(paymentService.cancelPayment("bkey", "order2", 500, CancelReason.SYSTEM_ERROR))
			.thenReturn(new PaymentCancelResponse(2L, "CANCELLED"));

		IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> saga.autoChargeWithCompensation(param)
		);
		assertTrue(ex.getMessage().contains("자동결제 재시도 실패 -> 취소 처리되었습니다."));
		verify(paymentService).chargeWithBillingKey(param);
		verify(paymentService).cancelPayment("bkey", "order2", 500, CancelReason.SYSTEM_ERROR);
	}

	// 7) cancelWithCompensation 성공
	@Test
	void cancelWithCompensation_success() {
		when(paymentService.cancelPayment("key", "order3", 200, CancelReason.USER_REQUEST))
			.thenReturn(new PaymentCancelResponse(3L, "CANCELLED"));

		assertDoesNotThrow(() ->
			saga.cancelWithCompensation("key", "order3", 200, CancelReason.USER_REQUEST)
		);
		verify(paymentService).cancelPayment("key", "order3", 200, CancelReason.USER_REQUEST);
	}

	// 8) cancelWithCompensation 실패 → recoverCancel 호출
	@Test
	void cancelWithCompensation_failure_triggersRecoverCancel() {
		when(paymentService.cancelPayment("key", "order3", 200, CancelReason.USER_REQUEST))
			.thenThrow(new RuntimeException("DB error"));

		IllegalStateException ex = assertThrows(
			IllegalStateException.class,
			() -> saga.cancelWithCompensation("key", "order3", 200, CancelReason.USER_REQUEST)
		);
		assertTrue(ex.getMessage().contains("취소 처리 재시도 실패"));
		verify(paymentService).cancelPayment("key", "order3", 200, CancelReason.USER_REQUEST);
	}
}