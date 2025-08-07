package com.grow.payment_service.payment.saga;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.PaymentSagaException;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompensationTransactionService 전체 테스트")
class CompensationTransactionServiceTest {

	@Mock
	private PaymentPersistenceService persistenceService;

	@InjectMocks
	private CompensationTransactionService service;

	private static final String ORDER_ID = "order-123";

	private Payment cancelledPayment;

	@BeforeEach
	void setUp() {
		// paymentId를 명시적으로 100L로 세팅하여 saveHistory 호출 검증 가능
		cancelledPayment = Payment.of(
			/* paymentId */ 100L,
			/* memberId  */ 1L,
			/* planId    */ 1L,
			/* orderId   */ ORDER_ID,
			/* paymentKey*/ null,
			/* billingKey*/ null,
			/* customerKey */ "cust",
			/* totalAmount */ 1000L,
			/* payStatus   */ PayStatus.CANCELLED,
			/* method      */ "CARD",
			/* failureReason */ null,
			/* cancelReason  */ CancelReason.SYSTEM_ERROR
		);
	}

	@Test
	@DisplayName("compensateApprovalFailure: 정상 흐름")
	void compensateApprovalFailure_success() {
		when(persistenceService.findByOrderId(ORDER_ID))
			.thenReturn(cancelledPayment);

		assertDoesNotThrow(() ->
			service.compensateApprovalFailure(ORDER_ID, new RuntimeException("boom"))
		);

		verify(persistenceService).findByOrderId(ORDER_ID);
		verify(persistenceService).saveForceCancelledPayment(any(Payment.class));
		verify(persistenceService).saveHistory(
			eq(100L),
			eq(PayStatus.CANCELLED),
			eq("보상-승인 취소 완료")
		);
	}

	@Test
	@DisplayName("compensateApprovalFailure: Persistence 예외 시 SagaException 발생")
	void compensateApprovalFailure_persistenceThrows() {
		when(persistenceService.findByOrderId(ORDER_ID))
			.thenThrow(new RuntimeException("DB down"));

		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> service.compensateApprovalFailure(ORDER_ID, new RuntimeException("boom"))
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_ERROR, ex.getErrorCode());
	}

	@Test
	@DisplayName("compensateCancelRequestFailure: 정상 흐름")
	void compensateCancelRequestFailure_success() {
		when(persistenceService.findByOrderId(ORDER_ID))
			.thenReturn(cancelledPayment);

		PaymentCancelResponse resp = service.compensateCancelRequestFailure(
			ORDER_ID, new RuntimeException("err")
		);

		assertEquals(100L, resp.getPaymentId());
		assertEquals("CANCELLED", resp.getStatus());

		verify(persistenceService).saveForceCancelledPayment(any(Payment.class));
		verify(persistenceService).saveHistory(
			eq(100L),
			eq(PayStatus.CANCELLED),
			eq("보상-취소요청 완료")
		);
	}

	@Test
	@DisplayName("compensateCancelRequestFailure: Persistence 예외 시 SagaException 발생")
	void compensateCancelRequestFailure_persistenceThrows() {
		when(persistenceService.findByOrderId(ORDER_ID))
			.thenThrow(new RuntimeException("DB fail"));

		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> service.compensateCancelRequestFailure(ORDER_ID, new RuntimeException("err"))
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_ERROR, ex.getErrorCode());
	}

	@Test
	@DisplayName("compensateCancelCompleteFailure: CANCEL_REQUESTED 상태에서 정상 처리")
	void compensateCancelCompleteFailure_success_whenRequested() {
		// CANCEL_REQUESTED 상태 Payment 준비 (paymentId 200L)
		Payment requested = Payment.of(
			200L, 1L, 1L, ORDER_ID,
			null, null, "cust", 1000L,
			PayStatus.CANCEL_REQUESTED, "CARD",
			null, CancelReason.USER_REQUEST
		);
		when(persistenceService.findByOrderId(ORDER_ID))
			.thenReturn(requested);

		PaymentCancelResponse resp = service.compensateCancelCompleteFailure(
			ORDER_ID, new RuntimeException("err")
		);

		assertEquals(200L, resp.getPaymentId());
		assertEquals("CANCELLED", resp.getStatus());

		verify(persistenceService).saveForceCancelledPayment(any(Payment.class));
		verify(persistenceService).saveHistory(
			eq(200L),
			eq(PayStatus.CANCELLED),
			eq("보상-취소완료 처리")
		);
	}

	@Test
	@DisplayName("compensateCancelCompleteFailure: 잘못된 상태에서 SagaException 발생")
	void compensateCancelCompleteFailure_invalidState() {
		when(persistenceService.findByOrderId(ORDER_ID))
			.thenReturn(cancelledPayment);  // 이미 CANCELLED

		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> service.compensateCancelCompleteFailure(ORDER_ID, new RuntimeException("err"))
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_ERROR, ex.getErrorCode());
	}

	@Test
	@DisplayName("compensateIssueKeyFailure: 항상 SagaException 발생")
	void compensateIssueKeyFailure_alwaysThrows() {
		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> service.compensateIssueKeyFailure(ORDER_ID, "bk", new RuntimeException("oops"))
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_ERROR, ex.getErrorCode());
		assertTrue(ex.getCause() instanceof IllegalStateException);
	}

	@Test
	@DisplayName("compensateAutoChargeFailure: 정상 흐름")
	void compensateAutoChargeFailure_success() {
		when(persistenceService.findByOrderId(ORDER_ID))
			.thenReturn(cancelledPayment);

		assertDoesNotThrow(() ->
			service.compensateAutoChargeFailure(ORDER_ID, new RuntimeException("err"))
		);

		verify(persistenceService).saveForceCancelledPayment(any(Payment.class));
		verify(persistenceService).saveHistory(
			eq(100L),
			eq(PayStatus.CANCELLED),
			eq("보상-자동결제 취소 완료")
		);
	}

	@Test
	@DisplayName("compensateAutoChargeFailure: Persistence 예외 시 SagaException 발생")
	void compensateAutoChargeFailure_persistenceThrows() {
		when(persistenceService.findByOrderId(ORDER_ID))
			.thenThrow(new RuntimeException("DB error"));

		PaymentSagaException ex = assertThrows(
			PaymentSagaException.class,
			() -> service.compensateAutoChargeFailure(ORDER_ID, new RuntimeException("err"))
		);
		assertEquals(ErrorCode.SAGA_COMPENSATE_ERROR, ex.getErrorCode());
	}
}