package com.grow.payment_service.payment.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.PaymentApplicationException;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.plan.domain.model.Plan;
import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.plan.domain.model.enums.PlanType;
import com.grow.payment_service.plan.domain.repository.PlanRepository;
import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentInitResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.service.OrderIdGenerator;
import com.grow.payment_service.payment.saga.PaymentSagaOrchestrator;
import com.grow.payment_service.subscription.application.service.SubscriptionHistoryApplicationService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentApplicationServiceImplTest {

	@Mock private PlanRepository planRepository;
	@Mock private OrderIdGenerator orderIdGenerator;
	@Mock private PaymentRepository paymentRepository;
	@Mock private PaymentHistoryRepository historyRepository;
	@Mock private PaymentSagaOrchestrator paymentSaga;
	@Mock private SubscriptionHistoryApplicationService subscriptionService;

	@InjectMocks
	private PaymentApplicationServiceImpl service;

	private final Long MEMBER_ID = 10L;
	private final Long PLAN_ID = 20L;
	private final String ORDER_ID = "order-001";

	@BeforeEach
	void setup() {
		// common default: subscription plan
		given(planRepository.findById(PLAN_ID))
			.willReturn(Optional.of(Plan.of(
				PLAN_ID,
				PlanType.SUBSCRIPTION,
				5000L,
				PlanPeriod.MONTHLY,
				"benefit"
			)));
	}

	@Test
	@DisplayName("initPaymentData: 성공")
	void initPaymentData_success() {
		given(orderIdGenerator.generate(MEMBER_ID)).willReturn(ORDER_ID);

		Payment dummy = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			null, null, "cust_" + MEMBER_ID, 5000L, "CARD"
		);
		given(paymentRepository.save(any(Payment.class))).willReturn(dummy);

		PaymentInitResponse resp = service.initPaymentData(MEMBER_ID, PLAN_ID, 5000);

		assertEquals(ORDER_ID, resp.getOrderId());
		assertEquals(5000, resp.getAmount());
		assertEquals(PLAN_ID, resp.getPlanId());
		assertEquals(PlanType.SUBSCRIPTION, resp.getPlanType());
		assertEquals(PlanPeriod.MONTHLY, resp.getPlanPeriod());
		then(paymentRepository).should().save(any(Payment.class));
		then(historyRepository).should().save(any());
		then(planRepository).should().findById(PLAN_ID);
	}

	@Test
	@DisplayName("initPaymentData: DB 오류 시 PaymentApplicationException")
	void initPaymentData_failure() {
		given(orderIdGenerator.generate(MEMBER_ID)).willReturn(ORDER_ID);
		given(paymentRepository.save(any(Payment.class)))
			.willThrow(new RuntimeException("DB fail"));

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.initPaymentData(MEMBER_ID, PLAN_ID, 5000)
		);
		assertEquals(ErrorCode.PAYMENT_INIT_ERROR, ex.getErrorCode());
	}

	@Test
	@DisplayName("confirmPayment: 정상 흐름 & 구독 갱신")
	void confirmPayment_success() {
		// given
		given(paymentSaga.confirmWithCompensation("pKey", ORDER_ID, 1234, "idem"))
			.willReturn(100L);

		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			null, null, "cust_" + MEMBER_ID, 1234L, "CARD"
		);
		// simulate paid.getPlanId() -> PLAN_ID
		// PaymentRepository.findById
		given(paymentRepository.findById(100L)).willReturn(Optional.of(paid));

		// when
		Long result = service.confirmPayment(
			MEMBER_ID, "pKey", ORDER_ID, 1234, "idem"
		);

		// then
		assertEquals(100L, result);
		then(paymentSaga).should().confirmWithCompensation("pKey", ORDER_ID, 1234, "idem");
		then(paymentRepository).should().findById(100L);
		then(subscriptionService).should().recordSubscriptionRenewal(MEMBER_ID, PlanPeriod.MONTHLY);
	}

	@Test
	@DisplayName("confirmPayment: 멤버 불일치 시 AccessDenied")
	void confirmPayment_memberMismatch() {
		given(paymentSaga.confirmWithCompensation(any(), any(), anyInt(), any()))
			.willReturn(200L);
		Payment paid = Payment.create(
			/*member*/ 999L, PLAN_ID, ORDER_ID,
			null, null, "cust_999", 1000L, "CARD"
		);
		given(paymentRepository.findById(200L)).willReturn(Optional.of(paid));

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.confirmPayment(MEMBER_ID, "p", ORDER_ID, 1000, "idem")
		);
		assertEquals(ErrorCode.PAYMENT_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("confirmPayment: SAGA 예외 시 PaymentApplicationException")
	void confirmPayment_sagaFail() {
		given(paymentSaga.confirmWithCompensation(any(), any(), anyInt(), any()))
			.willThrow(new RuntimeException("oops"));

		assertThrows(
			RuntimeException.class,  // raw exception from saga leaks
			() -> service.confirmPayment(MEMBER_ID, "p", ORDER_ID, 100, "idem")
		);
	}

	@Test
	@DisplayName("cancelPayment: 정상 호출")
	void cancelPayment_success() {
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			null, null, "cust_" + MEMBER_ID, 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));
		PaymentCancelResponse dummyRes = new PaymentCancelResponse(123L, "CANCELLED");
		given(paymentSaga.cancelWithCompensation(ORDER_ID, ORDER_ID, 1000, CancelReason.USER_REQUEST))
			.willReturn(dummyRes);

		PaymentCancelResponse res = service.cancelPayment(
			MEMBER_ID, ORDER_ID, ORDER_ID, 1000, CancelReason.USER_REQUEST
		);

		assertEquals(dummyRes, res);
		then(paymentSaga).should().cancelWithCompensation(ORDER_ID, ORDER_ID, 1000, CancelReason.USER_REQUEST);
	}

	@Test
	@DisplayName("cancelPayment: 멤버 불일치 시 AccessDenied")
	void cancelPayment_memberMismatch() {
		Payment paid = Payment.create(
			/*member*/999L, PLAN_ID, ORDER_ID,
			null, null, "cust_999", 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.cancelPayment(MEMBER_ID, ORDER_ID, ORDER_ID, 1000, CancelReason.USER_REQUEST)
		);
		assertEquals(ErrorCode.PAYMENT_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("issueBillingKey: 정상 호출")
	void issueBillingKey_success() {
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			null, null, "cust_" + MEMBER_ID, 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentIssueBillingKeyParam param = PaymentIssueBillingKeyParam.builder()
			.orderId(ORDER_ID)
			.authKey("auth")
			.customerKey("custKey")
			.build();
		PaymentIssueBillingKeyResponse dummy = new PaymentIssueBillingKeyResponse("bKey");
		given(paymentSaga.issueKeyWithCompensation(param)).willReturn(dummy);

		PaymentIssueBillingKeyResponse res = service.issueBillingKey(MEMBER_ID, param);

		assertEquals("bKey", res.getBillingKey());
		then(paymentSaga).should().issueKeyWithCompensation(param);
	}

	@Test
	@DisplayName("issueBillingKey: 멤버 불일치 시 AccessDenied")
	void issueBillingKey_memberMismatch() {
		Payment paid = Payment.create(
			/*member*/999L, PLAN_ID, ORDER_ID,
			null, null, "cust_999", 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentIssueBillingKeyParam param = PaymentIssueBillingKeyParam.builder()
			.orderId(ORDER_ID)
			.authKey("auth")
			.customerKey("custKey")
			.build();

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.issueBillingKey(MEMBER_ID, param)
		);
		assertEquals(ErrorCode.PAYMENT_ACCESS_DENIED, ex.getErrorCode());
	}

	@Test
	@DisplayName("chargeWithBillingKey: 정상 호출")
	void chargeWithBillingKey_success() {
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			null, "bKey", "cust_" + MEMBER_ID, 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentAutoChargeParam param = PaymentAutoChargeParam.builder()
			.billingKey("bKey")
			.customerKey("cust_" + MEMBER_ID)
			.amount(3000)
			.orderId(ORDER_ID)
			.orderName("name")
			.customerEmail("email")
			.customerName("name")
			.build();
		PaymentConfirmResponse dummy = new PaymentConfirmResponse(555L, "APPROVED");
		given(paymentSaga.autoChargeWithCompensation(param, "idem")).willReturn(dummy);

		PaymentConfirmResponse res = service.chargeWithBillingKey(MEMBER_ID, param, "idem");

		assertEquals("APPROVED", res.getPayStatus());
		then(paymentSaga).should().autoChargeWithCompensation(param, "idem");
	}

	@Test
	@DisplayName("chargeWithBillingKey: 멤버 불일치 시 AccessDenied")
	void chargeWithBillingKey_memberMismatch() {
		Payment paid = Payment.create(
			/*member*/999L, PLAN_ID, ORDER_ID,
			null, "bKey", "cust_999", 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentAutoChargeParam param = PaymentAutoChargeParam.builder()
			.billingKey("bKey")
			.customerKey("cust_999")
			.amount(3000)
			.orderId(ORDER_ID)
			.orderName("name")
			.customerEmail("email")
			.customerName("name")
			.build();

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.chargeWithBillingKey(MEMBER_ID, param, "idem")
		);
		assertEquals(ErrorCode.PAYMENT_ACCESS_DENIED, ex.getErrorCode());
	}
}