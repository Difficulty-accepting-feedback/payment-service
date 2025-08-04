package com.ggrow.payment_service.payment.saga;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.payment.global.exception.ErrorCode;
import com.grow.payment_service.payment.global.exception.PaymentSagaException;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingChargeResponse;
import com.grow.payment_service.payment.saga.CompensationTransactionService;
import com.grow.payment_service.payment.saga.RetryablePersistenceService;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetryablePersistenceService Fallback 메서드 단위 테스트 (수정)")
class RetryablePersistenceServiceTest {

	@Mock
	PaymentPersistenceService persistenceService;
	@Mock
	PaymentGatewayPort gatewayPort;
	@Mock
	CompensationTransactionService compTx;
	@InjectMocks
	RetryablePersistenceService svc;

	private final String paymentKey = "payKey";
	private final String billingKey = "billKey";
	private final String orderId    = "order-123";
	private final int amount        = 1000;
	private final TossBillingChargeResponse tossRes = mock(TossBillingChargeResponse.class);
	private final RuntimeException cause = new RuntimeException("fail");

	@Test
	@DisplayName("recoverConfirm: 외부 cancel + 보상 호출 후 SAGA_COMPENSATE_COMPLETED 예외")
	void recoverConfirm_callsCancelAndCompensate_thenSagaCompleted() {
		// cancelPayment은 TossCancelResponse 반환 타입이므로 null 리턴 stub
		when(gatewayPort.cancelPayment(
			eq(paymentKey), eq(CancelReason.SYSTEM_ERROR.name()), eq(amount), eq("보상 취소")
		)).thenReturn(null);
		doNothing().when(compTx).compensateApprovalFailure(orderId, cause);

		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> svc.recoverConfirm(paymentKey, orderId, amount, cause)
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_COMPLETED, ex.getErrorCode());

		verify(gatewayPort).cancelPayment(
			eq(paymentKey), eq(CancelReason.SYSTEM_ERROR.name()), eq(amount), eq("보상 취소")
		);
		verify(compTx).compensateApprovalFailure(orderId, cause);
	}

	@Test
	@DisplayName("recoverCancelRequest: 보상 요청으로 위임")
	void recoverCancelRequest_delegatesToCompensate() {
		PaymentCancelResponse dummy = new PaymentCancelResponse(1L, "CANCELLED");
		when(compTx.compensateCancelRequestFailure(orderId, cause)).thenReturn(dummy);

		PaymentCancelResponse resp = svc.recoverCancelRequest(
			paymentKey, orderId, amount, CancelReason.USER_REQUEST, cause
		);
		assertSame(dummy, resp);
		verify(compTx).compensateCancelRequestFailure(orderId, cause);
	}

	@Test
	@DisplayName("recoverCancelComplete: 보상 완료로 위임")
	void recoverCancelComplete_delegatesToCompensate() {
		PaymentCancelResponse dummy = new PaymentCancelResponse(2L, "CANCELLED");
		when(compTx.compensateCancelCompleteFailure(orderId, cause)).thenReturn(dummy);

		PaymentCancelResponse resp = svc.recoverCancelComplete(orderId, cause);
		assertSame(dummy, resp);
		verify(compTx).compensateCancelCompleteFailure(orderId, cause);
	}

	@Test
	@DisplayName("recoverIssueKey: 항상 SAGA_COMPENSATE_ERROR 예외")
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
		// cancelPayment stub
		when(gatewayPort.cancelPayment(
			eq(billingKey), eq(CancelReason.SYSTEM_ERROR.name()), eq(amount), eq("보상-자동 결제 취소")
		)).thenReturn(null);
		doNothing().when(compTx).compensateAutoChargeFailure(orderId, cause);

		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> svc.recoverAutoCharge(billingKey, orderId, amount, tossRes, cause)
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_COMPLETED, ex.getErrorCode());

		verify(gatewayPort).cancelPayment(
			eq(billingKey), eq(CancelReason.SYSTEM_ERROR.name()), eq(amount), eq("보상-자동 결제 취소")
		);
		verify(compTx).compensateAutoChargeFailure(orderId, cause);
	}
}