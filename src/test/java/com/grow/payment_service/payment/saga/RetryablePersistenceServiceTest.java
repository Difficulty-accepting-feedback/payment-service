package com.grow.payment_service.payment.saga;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.PaymentSagaException;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingChargeResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetryablePersistenceService 단위 테스트")
class RetryablePersistenceServiceTest {

	@Mock PaymentPersistenceService persistenceService;
	@Mock PaymentGatewayPort gatewayPort;
	@Mock CompensationTransactionService compTx;
	@InjectMocks RetryablePersistenceService svc;

	private final String paymentKey = "payKey";
	private final String billingKey = "billKey";
	private final String orderId    = "order-123";
	private final int amount        = 1000;
	private final TossBillingChargeResponse tossRes = mock(TossBillingChargeResponse.class);
	private final RuntimeException cause = new RuntimeException("fail");

	@Test
	@DisplayName("recoverConfirm: 외부 cancel + 보상 호출 후 SAGA_COMPENSATE_COMPLETED 예외")
	void recoverConfirm_callsCancelAndCompensate_thenSagaCompleted() {
		when(gatewayPort.cancelPayment(eq(paymentKey), eq(CancelReason.SYSTEM_ERROR.name()), eq(amount), eq("보상 취소")))
			.thenReturn(null);
		doNothing().when(compTx).compensateApprovalFailure(orderId, cause);

		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> svc.recoverConfirm(paymentKey, orderId, amount, cause)
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_COMPLETED, ex.getErrorCode());

		verify(gatewayPort).cancelPayment(eq(paymentKey), eq(CancelReason.SYSTEM_ERROR.name()), eq(amount), eq("보상 취소"));
		verify(compTx).compensateApprovalFailure(orderId, cause);
	}

	@Test
	@DisplayName("recoverCancelRequest: 보상 요청 위임")
	void recoverCancelRequest_delegatesToCompensate() {
		PaymentCancelResponse dummy = new PaymentCancelResponse(1L, "CANCELLED");
		when(compTx.compensateCancelRequestFailure(orderId, cause)).thenReturn(dummy);

		PaymentCancelResponse resp = svc.recoverCancelRequest(paymentKey, orderId, amount, CancelReason.USER_REQUEST, cause);
		assertSame(dummy, resp);
		verify(compTx).compensateCancelRequestFailure(orderId, cause);
	}

	@Test
	@DisplayName("recoverCancelComplete: 보상 완료 위임")
	void recoverCancelComplete_delegatesToCompensate() {
		PaymentCancelResponse dummy = new PaymentCancelResponse(2L, "CANCELLED");
		when(compTx.compensateCancelCompleteFailure(orderId, cause)).thenReturn(dummy);

		PaymentCancelResponse resp = svc.recoverCancelComplete(orderId, cause);
		assertSame(dummy, resp);
		verify(compTx).compensateCancelCompleteFailure(orderId, cause);
	}

	@Test
	@DisplayName("recoverIssueKey: compTx 예외 → SAGA_COMPENSATE_ERROR")
	void recoverIssueKey_throwsSagaError() {
		doThrow(new PaymentSagaException(ErrorCode.SAGA_COMPENSATE_ERROR, new IllegalStateException("err")))
			.when(compTx).compensateIssueKeyFailure(orderId, billingKey, cause);

		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> svc.recoverIssueKey(orderId, billingKey, cause)
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_ERROR, ex.getErrorCode());
	}

	@Test
	@DisplayName("recoverAutoCharge: 외부 cancel + 보상 호출 후 SAGA_COMPENSATE_COMPLETED 예외")
	void recoverAutoCharge_callsCancelAndCompensate_thenSagaCompleted() {
		when(gatewayPort.cancelPayment(eq(billingKey), eq(CancelReason.SYSTEM_ERROR.name()), eq(amount), eq("보상-자동 결제 취소")))
			.thenReturn(null);
		doNothing().when(compTx).compensateAutoChargeFailure(orderId, cause);

		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> svc.recoverAutoCharge(billingKey, orderId, amount, tossRes, cause)
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_COMPLETED, ex.getErrorCode());

		verify(gatewayPort).cancelPayment(eq(billingKey), eq(CancelReason.SYSTEM_ERROR.name()), eq(amount), eq("보상-자동 결제 취소"));
		verify(compTx).compensateAutoChargeFailure(orderId, cause);
	}

	@Test
	@DisplayName("saveConfirmation: 저장 성공 시 paymentId 반환 (paymentKey 포함)")
	void saveConfirmation_success() {
		when(persistenceService.savePaymentConfirmation(orderId, paymentKey)).thenReturn(10L);

		Long id = svc.saveConfirmation(paymentKey, orderId, amount);

		assertEquals(10L, id);
		verify(persistenceService).savePaymentConfirmation(orderId, paymentKey);
	}

	@Test
	@DisplayName("saveCancelRequest: 저장 성공")
	void saveCancelRequest_success() {
		PaymentCancelResponse dummy = new PaymentCancelResponse(11L, "CANCEL_REQUESTED");
		when(persistenceService.requestCancel(orderId, CancelReason.USER_REQUEST, amount)).thenReturn(dummy);

		PaymentCancelResponse res = svc.saveCancelRequest(paymentKey, orderId, amount, CancelReason.USER_REQUEST);

		assertSame(dummy, res);
		verify(persistenceService).requestCancel(orderId, CancelReason.USER_REQUEST, amount);
	}

	@Test
	@DisplayName("saveCancelComplete: 저장 성공")
	void saveCancelComplete_success() {
		PaymentCancelResponse dummy = new PaymentCancelResponse(12L, "CANCELLED");
		when(persistenceService.completeCancel(orderId)).thenReturn(dummy);

		PaymentCancelResponse res = svc.saveCancelComplete(orderId);

		assertSame(dummy, res);
		verify(persistenceService).completeCancel(orderId);
	}

	@Test
	@DisplayName("saveBillingKey: 저장 성공")
	void saveBillingKey_success() {
		var dummy = new PaymentIssueBillingKeyResponse("bk-001");
		when(persistenceService.saveBillingKeyRegistration(orderId, "bk-001")).thenReturn(dummy);

		var res = svc.saveBillingKey(orderId, "bk-001");

		assertEquals("bk-001", res.getBillingKey());
		verify(persistenceService).saveBillingKeyRegistration(orderId, "bk-001");
	}

	@Test
	@DisplayName("saveAutoCharge: 저장 성공")
	void saveAutoCharge_success() {
		PaymentConfirmResponse dummy = new PaymentConfirmResponse(99L, "DONE", "e@mail", "name");
		when(persistenceService.saveAutoChargeResult(orderId, tossRes)).thenReturn(dummy);

		var res = svc.saveAutoCharge(billingKey, orderId, amount, tossRes);

		assertEquals(99L, res.getPaymentId());
		assertEquals("DONE", res.getPayStatus());
		verify(persistenceService).saveAutoChargeResult(orderId, tossRes);
	}

	@Test
	@DisplayName("recoverConfirm: 보상 중 에러 → SAGA_COMPENSATE_ERROR")
	void recoverConfirm_compensationError() {
		when(gatewayPort.cancelPayment(eq(paymentKey), anyString(), anyInt(), anyString())).thenReturn(null);
		doThrow(new IllegalStateException("comp-err")).when(compTx).compensateApprovalFailure(orderId, cause);

		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> svc.recoverConfirm(paymentKey, orderId, amount, cause)
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_ERROR, ex.getErrorCode());
	}

	@Test
	@DisplayName("recoverAutoCharge: 보상 중 에러 → SAGA_COMPENSATE_ERROR")
	void recoverAutoCharge_compensationError() {
		when(gatewayPort.cancelPayment(eq(billingKey), anyString(), anyInt(), anyString())).thenReturn(null);
		doThrow(new IllegalStateException("comp-err")).when(compTx).compensateAutoChargeFailure(orderId, cause);

		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> svc.recoverAutoCharge(billingKey, orderId, amount, tossRes, cause)
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_ERROR, ex.getErrorCode());
	}

	@Test
	@DisplayName("recoverIssueKey: 보상 정상 종료 → SAGA_COMPENSATE_COMPLETED")
	void recoverIssueKey_completed() {
		doNothing().when(compTx).compensateIssueKeyFailure(orderId, billingKey, cause);

		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> svc.recoverIssueKey(orderId, billingKey, cause)
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_COMPLETED, ex.getErrorCode());
		verify(compTx).compensateIssueKeyFailure(orderId, billingKey, cause);
	}
}