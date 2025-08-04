package com.grow.payment_service.payment.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import static com.grow.payment_service.payment.domain.model.enums.PayStatus.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.grow.payment_service.payment.domain.exception.PaymentDomainException;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;

@DisplayName("Payment 도메인 모델 전이 메서드 테스트")
class PaymentTest {

	// 공통 상수
	private static final Long PAYMENT_ID     = 1L;
	private static final Long MEMBER_ID      = 2L;
	private static final Long PLAN_ID        = 3L;
	private static final String ORDER_ID     = "order-123";
	private static final String PAYMENT_KEY  = "pay-key";
	private static final String BILLING_KEY  = "bill-key";
	private static final String CUSTOMER_KEY = "cust-key";
	private static final Long AMOUNT         = 10_000L;
	private static final String METHOD       = "CARD";

	// FailureReason 및 CancelReason은 null로 테스트; 실제 값으로 교체 가능
	private static final Object DUMMY_REASON = null;

	@Test
	@DisplayName("create() 시 기본값 검증")
	void create_initialState() {
		Payment p = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
			AMOUNT, METHOD
		);

		assertAll("create() 기본 상태",
			() -> assertNull(p.getPaymentId(),       "paymentId는 null"),
			() -> assertEquals(READY, p.getPayStatus(), "상태는 READY"),
			() -> assertEquals(BILLING_KEY, p.getBillingKey(), "billingKey 보존"),
			() -> assertNull(p.getFailureReason(),  "failureReason은 null"),
			() -> assertNull(p.getCancelReason(),   "cancelReason은 null")
		);
	}

	@Nested
	@DisplayName("requestCancel(reason)")
	class RequestCancelTest {

		@Test
		@DisplayName("READY → CANCEL_REQUESTED 로 전이")
		void success_fromReady() {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, READY, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			Payment updated = original.requestCancel((com.grow.payment_service.payment.domain.model.enums.CancelReason) DUMMY_REASON);

			assertAll("requestCancel 성공",
				() -> assertEquals(CANCEL_REQUESTED, updated.getPayStatus()),
				() -> assertEquals(DUMMY_REASON,    updated.getCancelReason())
			);
		}

		@ParameterizedTest(name = "상태 {0} 에서 requestCancel() 호출 시 예외")
		@EnumSource(
			value = PayStatus.class,
			mode  = EnumSource.Mode.EXCLUDE,
			names = {"READY", "DONE"}  // READY, DONE만 허용
		)
		void failure_invalidState(PayStatus invalid) {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, invalid, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			assertThrows(
				PaymentDomainException.class,
				() -> original.requestCancel((com.grow.payment_service.payment.domain.model.enums.CancelReason) DUMMY_REASON)
			);
		}
	}

	@Nested
	@DisplayName("completeCancel()")
	class CompleteCancelTest {

		@Test
		@DisplayName("CANCEL_REQUESTED → CANCELLED 로 전이")
		void success_fromRequested() {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, CANCEL_REQUESTED, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/CancelReason.USER_REQUEST
			);

			Payment updated = original.completeCancel();

			assertEquals(CANCELLED, updated.getPayStatus());
		}

		@ParameterizedTest(name = "상태 {0} 에서 completeCancel() 호출 시 예외")
		@EnumSource(
			value = PayStatus.class,
			mode  = EnumSource.Mode.EXCLUDE,
			names = {"CANCEL_REQUESTED"}  // 오직 CANCEL_REQUESTED만 허용
		)
		void failure_invalidState(PayStatus invalid) {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, invalid, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			assertThrows(
				PaymentDomainException.class,
				original::completeCancel
			);
		}
	}

	@Nested
	@DisplayName("registerBillingKey(billingKey)")
	class RegisterBillingKeyTest {

		@Test
		@DisplayName("READY 상태에서 호출 시 AUTO_BILLING_READY로 전이")
		void success_fromReady() {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, /*billingKey*/null, CUSTOMER_KEY,
				AMOUNT, READY, METHOD,
				/*failureReason=*/null, /*cancelReason=*/null
			);

			Payment updated = original.registerBillingKey(BILLING_KEY);

			assertAll(
				() -> assertEquals(AUTO_BILLING_READY, updated.getPayStatus()),
				() -> assertEquals(BILLING_KEY,        updated.getBillingKey())
			);
		}

		@ParameterizedTest(name = "{0} 상태에서 registerBillingKey() 호출 시 예외 발생")
		@EnumSource(
			value = PayStatus.class,
			mode  = EnumSource.Mode.INCLUDE,
			names = {
				"IN_PROGRESS", "DONE",
				"CANCEL_REQUESTED", "CANCELLED",
				"AUTO_BILLING_READY", "AUTO_BILLING_IN_PROGRESS",
				"AUTO_BILLING_APPROVED", "AUTO_BILLING_FAILED",
				"ABORTED", "EXPIRED", "FAILED"
			}
		)
		void failure_invalidState(PayStatus invalid) {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, null, CUSTOMER_KEY,
				AMOUNT, invalid, METHOD,
				/*failureReason=*/null, /*cancelReason=*/null
			);

			assertThrows(
				PaymentDomainException.class,
				() -> original.registerBillingKey(BILLING_KEY),
				"상태 " + invalid + " 에서 예외가 발생해야 합니다."
			);
		}
	}

	@Nested
	@DisplayName("startAutoBilling()")
	class StartAutoBillingTest {

		@Test
		@DisplayName("AUTO_BILLING_READY → AUTO_BILLING_IN_PROGRESS 로 전이")
		void success_fromReady() {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, AUTO_BILLING_READY, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			Payment updated = original.startAutoBilling();

			assertEquals(AUTO_BILLING_IN_PROGRESS, updated.getPayStatus());
		}

		@ParameterizedTest(name = "상태 {0} 에서 startAutoBilling() 호출 시 예외")
		@EnumSource(
			value = PayStatus.class,
			mode  = EnumSource.Mode.EXCLUDE,
			names = {"AUTO_BILLING_READY"}  // 오직 READY 단계만 허용
		)
		void failure_invalidState(PayStatus invalid) {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, invalid, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			assertThrows(
				PaymentDomainException.class,
				original::startAutoBilling
			);
		}
	}

	@Nested
	@DisplayName("approveAutoBilling()")
	class ApproveAutoBillingTest {

		@Test
		@DisplayName("AUTO_BILLING_READY → AUTO_BILLING_APPROVED 로 전이")
		void success_fromReady() {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, AUTO_BILLING_READY, METHOD,
				/*failureReason=*/null, /*cancelReason=*/null
			);
			Payment updated = original.approveAutoBilling();
			assertEquals(AUTO_BILLING_APPROVED, updated.getPayStatus());
		}

		@Test
		@DisplayName("AUTO_BILLING_IN_PROGRESS → AUTO_BILLING_APPROVED 로 전이")
		void success_fromInProgress() {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, AUTO_BILLING_IN_PROGRESS, METHOD,
				/*failureReason=*/null, /*cancelReason=*/null
			);
			Payment updated = original.approveAutoBilling();
			assertEquals(AUTO_BILLING_APPROVED, updated.getPayStatus());
		}

		@ParameterizedTest(name = "상태 {0} 에서 approveAutoBilling() 호출 시 예외 발생")
		@EnumSource(
			value = PayStatus.class,
			mode  = EnumSource.Mode.EXCLUDE,
			names = {
				"AUTO_BILLING_READY",
				"AUTO_BILLING_IN_PROGRESS"  // 이제 이 상태도 제외
			}
		)
		void failure_invalidState(PayStatus invalid) {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, invalid, METHOD,
				/*failureReason=*/null, /*cancelReason=*/null
			);
			assertThrows(
				PaymentDomainException.class,
				original::approveAutoBilling
			);
		}
	}

	@Nested
	@DisplayName("failAutoBilling(reason)")
	class FailAutoBillingTest {

		@Test
		@DisplayName("AUTO_BILLING_READY → AUTO_BILLING_FAILED 로 전이")
		void success_fromReady() {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, AUTO_BILLING_READY, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			Payment updated = original.failAutoBilling((com.grow.payment_service.payment.domain.model.enums.FailureReason) DUMMY_REASON);

			assertAll(
				() -> assertEquals(AUTO_BILLING_FAILED, updated.getPayStatus()),
				() -> assertEquals(DUMMY_REASON,        updated.getFailureReason())
			);
		}

		@ParameterizedTest(name = "상태 {0} 에서 failAutoBilling() 호출 시 예외")
		@EnumSource(
			value = PayStatus.class,
			mode  = EnumSource.Mode.EXCLUDE,
			names = {"AUTO_BILLING_READY", "AUTO_BILLING_IN_PROGRESS"}  // READY, IN_PROGRESS만 허용
		)
		void failure_invalidState(PayStatus invalid) {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, invalid, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			assertThrows(
				PaymentDomainException.class,
				() -> original.failAutoBilling((com.grow.payment_service.payment.domain.model.enums.FailureReason) DUMMY_REASON)
			);
		}
	}

	@Nested
	@DisplayName("resetForNextCycle()")
	class ResetForNextCycleTest {

		@Test
		@DisplayName("AUTO_BILLING_APPROVED → AUTO_BILLING_READY 로 재설정")
		void success_fromApproved() {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, AUTO_BILLING_APPROVED, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			Payment updated = original.resetForNextCycle();

			assertEquals(AUTO_BILLING_READY, updated.getPayStatus());
			assertNull(updated.getFailureReason(), "failureReason 초기화");
			assertNull(updated.getCancelReason(),  "cancelReason 초기화");
		}

		@ParameterizedTest(name = "상태 {0} 에서 resetForNextCycle() 호출 시 예외")
		@EnumSource(
			value = PayStatus.class,
			mode  = EnumSource.Mode.EXCLUDE,
			names = {"AUTO_BILLING_APPROVED"}  // 오직 승인된 상태만 허용
		)
		void failure_invalidState(PayStatus invalid) {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, invalid, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			assertThrows(
				PaymentDomainException.class,
				original::resetForNextCycle
			);
		}
	}

	@Nested
	@DisplayName("clearBillingKey() [abortAutoBilling]")
	class ClearBillingKeyTest {

		@Test
		@DisplayName("AUTO_BILLING_READY → ABORTED + billingKey 제거")
		void success_fromReady() {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, AUTO_BILLING_READY, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			Payment updated = original.clearBillingKey();

			assertAll(
				() -> assertEquals(ABORTED, updated.getPayStatus(), "상태는 ABORTED"),
				() -> assertNull(updated.getBillingKey(), "billingKey 제거")
			);
		}

		@Test
		@DisplayName("AUTO_BILLING_IN_PROGRESS → ABORTED + billingKey 제거")
		void success_fromInProgress() {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, AUTO_BILLING_IN_PROGRESS, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			Payment updated = original.clearBillingKey();

			assertAll(
				() -> assertEquals(ABORTED, updated.getPayStatus(), "상태는 ABORTED"),
				() -> assertNull(updated.getBillingKey(), "billingKey 제거")
			);
		}

		@ParameterizedTest(name = "상태 {0} 에서 clearBillingKey() 호출 시 예외 발생")
		@EnumSource(
			value = PayStatus.class,
			mode = EnumSource.Mode.EXCLUDE,
			names = {"AUTO_BILLING_READY", "AUTO_BILLING_IN_PROGRESS", "AUTO_BILLING_FAILED"}  // READY, IN_PROGRESS, FAILED만 허용
		)
		void failure_invalidState(PayStatus invalid) {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, invalid, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			assertThrows(
				PaymentDomainException.class,
				original::clearBillingKey,
				() -> invalid + " 상태에서는 clearBillingKey() 예외"
			);
		}
	}

	@Test
	@DisplayName("forceCancel(reason) – 상태 검증 없이 항상 CANCELLED")
	void forceCancel_always() {
		for (PayStatus from : PayStatus.values()) {
			Payment original = Payment.of(
				PAYMENT_ID, MEMBER_ID, PLAN_ID, ORDER_ID,
				PAYMENT_KEY, BILLING_KEY, CUSTOMER_KEY,
				AMOUNT, from, METHOD,
				/*failureReason=*/null,
				/*cancelReason=*/null
			);

			Payment updated = original.forceCancel((com.grow.payment_service.payment.domain.model.enums.CancelReason) DUMMY_REASON);
			assertEquals(CANCELLED, updated.getPayStatus(),
				() -> from + " → forceCancel 시 CANCELLED 되어야 함");
			assertEquals(DUMMY_REASON, updated.getCancelReason());
		}
	}
}