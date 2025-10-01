package com.grow.payment_service.payment.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.PaymentApplicationException;
import com.grow.payment_service.global.metrics.PaymentMetrics;
import com.grow.payment_service.payment.application.dto.*;
import com.grow.payment_service.payment.domain.exception.PaymentDomainException;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.domain.service.OrderIdGenerator;
import com.grow.payment_service.payment.infra.client.MemberClient;
import com.grow.payment_service.payment.infra.client.MemberInfoResponse;
import com.grow.payment_service.payment.saga.PaymentSagaOrchestrator;
import com.grow.payment_service.plan.domain.model.Plan;
import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.plan.domain.model.enums.PlanType;
import com.grow.payment_service.plan.domain.repository.PlanRepository;
import com.grow.payment_service.subscription.application.service.SubscriptionHistoryApplicationService;

import com.grow.payment_service.payment.application.event.PaymentNotificationProducer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentApplicationServiceImplTest {

	@Mock private PlanRepository planRepository;
	@Mock private OrderIdGenerator orderIdGenerator;
	@Mock private PaymentRepository paymentRepository;
	@Mock private PaymentHistoryRepository historyRepository;
	@Mock private PaymentSagaOrchestrator paymentSaga;
	@Mock private SubscriptionHistoryApplicationService subscriptionService;
	@Mock private MemberClient memberClient;
	@Mock private PaymentMetrics metrics;

	@Mock private PaymentNotificationProducer notificationProducer;

	@InjectMocks
	private PaymentApplicationServiceImpl service;

	private final Long MEMBER_ID = 10L;
	private final Long PLAN_ID   = 20L;
	private final String ORDER_ID = "order-001";

	@BeforeEach
	void setup() {
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

		// init 단계는 알림 발행 없음
		then(notificationProducer).shouldHaveNoInteractions();
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

		then(notificationProducer).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("confirmPayment: 정상 흐름 & 구독 갱신")
	void confirmPayment_success() {
		MemberInfoResponse profile = new MemberInfoResponse(1L, "test@example.com", "TestUser");
		given(memberClient.getMyInfo(MEMBER_ID))
			.willReturn(new RsData<>("200", "OK", profile));

		given(paymentSaga.confirmWithCompensation(
			"pKey", ORDER_ID, 1234, "idem", "test@example.com", "TestUser"
		)).willReturn(100L);

		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			null, null, "cust_" + MEMBER_ID, 1234L, "CARD"
		);
		given(paymentRepository.findById(100L)).willReturn(Optional.of(paid));

		Long result = service.confirmPayment(
			MEMBER_ID, "pKey", ORDER_ID, 1234, "idem"
		);

		assertEquals(100L, result);
		then(paymentSaga).should().confirmWithCompensation(
			"pKey", ORDER_ID, 1234, "idem", "test@example.com", "TestUser"
		);
		then(paymentRepository).should().findById(100L);
		then(subscriptionService).should().recordSubscriptionRenewal(MEMBER_ID, PlanPeriod.MONTHLY);

		// ✅ 승인 성공 알림 발행 검증
		then(notificationProducer).should().paymentApproved(MEMBER_ID, ORDER_ID, 1234);
	}

	@Test
	@DisplayName("confirmPayment: 멤버 불일치 시 PaymentApplicationException(원인: PaymentDomainException)")
	void confirmPayment_memberMismatch() {
		MemberInfoResponse profile = new MemberInfoResponse(1L,"email", "name");
		given(memberClient.getMyInfo(MEMBER_ID))
			.willReturn(new RsData<>("200","OK", profile));

		given(paymentSaga.confirmWithCompensation(
			anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()
		)).willReturn(200L);

		Payment paid = Payment.create(
			999L, PLAN_ID, ORDER_ID,
			null, null, "cust_999", 1000L, "CARD"
		);
		given(paymentRepository.findById(200L)).willReturn(Optional.of(paid));

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.confirmPayment(MEMBER_ID, "pKey", ORDER_ID, 1000, "idem")
		);
		assertTrue(ex.getCause() instanceof PaymentDomainException);

		then(notificationProducer).should(never()).paymentApproved(anyLong(), anyString(), anyInt());
	}

	@Test
	@DisplayName("confirmPayment: SAGA 예외 시 RuntimeException 그대로 노출 (알림 없음)")
	void confirmPayment_sagaFail() {
		MemberInfoResponse profile = new MemberInfoResponse(1L,"email", "name");
		given(memberClient.getMyInfo(MEMBER_ID))
			.willReturn(new RsData<>("200","OK", profile));

		given(paymentSaga.confirmWithCompensation(
			anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()
		)).willThrow(new RuntimeException("oops"));

		assertThrows(
			RuntimeException.class,
			() -> service.confirmPayment(MEMBER_ID, "pKey", ORDER_ID, 100, "idem")
		);

		// ❌ 승인 실패는 프론트/위젯에서 안내 → 푸시 알림 없음
		then(notificationProducer).should(never()).paymentApproved(anyLong(), anyString(), anyInt());
	}

	@Test
	@DisplayName("cancelPayment(구독): 7일 이내 전액 환불 → 서버가 DB의 paymentKey로 SAGA 호출되고 금액은 전체금액")
	void cancelPayment_success() {
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			"pKey-1", null, "cust_" + MEMBER_ID, 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		given(historyRepository.findLastByPaymentIdAndStatuses(any(), anyList()))
			.willReturn(Optional.of(
				PaymentHistory.create(1L, PayStatus.DONE, "결제 완료")
			));

		PaymentCancelResponse dummyRes = new PaymentCancelResponse(123L, "CANCELLED");
		given(paymentSaga.cancelWithCompensation(
			"pKey-1", ORDER_ID, 3000, CancelReason.USER_REQUEST
		)).willReturn(dummyRes);

		PaymentCancelResponse res = service.cancelPayment(
			MEMBER_ID, ORDER_ID, 1000, CancelReason.USER_REQUEST
		);

		assertEquals(dummyRes, res);
		then(paymentSaga).should().cancelWithCompensation(
			"pKey-1", ORDER_ID, 3000, CancelReason.USER_REQUEST
		);

		// ✅ 취소 완료 알림 발행 검증 (전액)
		then(notificationProducer).should().cancelled(MEMBER_ID, ORDER_ID, 3000);
	}

	@Test
	@DisplayName("cancelPayment: 멤버 불일치 시 도메인 예외 발생")
	void cancelPayment_memberMismatch() {
		Payment paid = Payment.create(
			999L, PLAN_ID, ORDER_ID,
			"pKey-x", null, "cust_999", 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		assertThrows(
			PaymentDomainException.class,
			() -> service.cancelPayment(MEMBER_ID, ORDER_ID, 1000, CancelReason.USER_REQUEST)
		);

		// ❌ 알림 없음
		then(notificationProducer).should(never()).cancelled(anyLong(), anyString(), anyInt());
		then(notificationProducer).should(never()).cancelScheduled(anyLong(), anyString());
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

		// ✅ 빌링키 발급 알림
		then(notificationProducer).should().billingKeyIssued(MEMBER_ID, ORDER_ID);
	}

	@Test
	@DisplayName("issueBillingKey: 멤버 불일치 시 도메인 예외 발생")
	void issueBillingKey_memberMismatch() {
		Payment paid = Payment.create(
			999L, PLAN_ID, ORDER_ID,
			null, null, "cust_999", 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentIssueBillingKeyParam param = PaymentIssueBillingKeyParam.builder()
			.orderId(ORDER_ID)
			.authKey("auth")
			.customerKey("custKey")
			.build();

		assertThrows(
			PaymentDomainException.class,
			() -> service.issueBillingKey(MEMBER_ID, param)
		);

		// ❌ 알림 없음
		then(notificationProducer).should(never()).billingKeyIssued(anyLong(), anyString());
	}

	@Test
	@DisplayName("chargeWithBillingKey: 정상 호출(자동결제)")
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

		// ※ DTO 생성자 시그니처는 프로젝트 현재 정의에 맞게 유지
		PaymentConfirmResponse dummy = new PaymentConfirmResponse(
			555L, "AUTO_BILLING_APPROVED", "paymentKey", "email", "name"
		);
		given(paymentSaga.autoChargeWithCompensation(param, "idem")).willReturn(dummy);

		PaymentConfirmResponse res = service.chargeWithBillingKey(MEMBER_ID, param, "idem");

		assertEquals("AUTO_BILLING_APPROVED", res.getPayStatus());
		then(paymentSaga).should().autoChargeWithCompensation(param, "idem");

		// ✅ 자동결제 승인 알림
		then(notificationProducer).should().autoBillingApproved(MEMBER_ID, ORDER_ID, 3000);
	}

	@Test
	@DisplayName("chargeWithBillingKey: 멤버 불일치 시 도메인 예외 발생")
	void chargeWithBillingKey_memberMismatch() {
		Payment paid = Payment.create(
			999L, PLAN_ID, ORDER_ID,
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

		assertThrows(
			PaymentDomainException.class,
			() -> service.chargeWithBillingKey(MEMBER_ID, param, "idem")
		);

		// ❌ 알림 없음
		then(notificationProducer).should(never()).autoBillingApproved(anyLong(), anyString(), anyInt());
	}

	@Test
	@DisplayName("expireIfReady: READY → ABORTED로 전이하고 저장/이력 기록")
	void expireIfReady_readyToAborted_success() {
		Payment ready = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			null, null, "cust_" + MEMBER_ID, 5000L, "CARD"
		);
		given(paymentRepository.findByOrderIdForUpdate(ORDER_ID))
			.willReturn(Optional.of(ready));

		service.expireIfReady(MEMBER_ID, ORDER_ID);

		ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
		then(paymentRepository).should().save(paymentCaptor.capture());
		assertEquals(PayStatus.ABORTED, paymentCaptor.getValue().getPayStatus());

		then(historyRepository).should(times(1)).save(any());
		then(paymentRepository).should().findByOrderIdForUpdate(ORDER_ID);

		// 만료는 알림 발행 대상 아님
		then(notificationProducer).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("expireIfReady: 주문 없음 → PaymentApplicationException(ORDER_NOT_FOUND)")
	void expireIfReady_orderNotFound_throws() {
		given(paymentRepository.findByOrderIdForUpdate(ORDER_ID))
			.willReturn(Optional.empty());

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.expireIfReady(MEMBER_ID, ORDER_ID)
		);

		assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());
		then(paymentRepository).should().findByOrderIdForUpdate(ORDER_ID);
		then(paymentRepository).should(never()).save(any());
		then(historyRepository).should(never()).save(any());

		then(notificationProducer).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("cancelPayment(구독): 7일 초과 → 다음 달부터 해지 예약 (billingKey 제거 + ABORTED 저장)")
	void cancelPayment_subscription_after7days_abortAndClearKey() {
		Payment paid = Payment.of(
			1L, MEMBER_ID, PLAN_ID, ORDER_ID,
			"pKey-1", "bKey-1", "cust_" + MEMBER_ID,
			3000L, PayStatus.AUTO_BILLING_APPROVED,
			"CARD", null, null
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		// 최근 승인 이력 없음(= 7일 초과로 간주)
		given(historyRepository.findLastByPaymentIdAndStatuses(any(), anyList()))
			.willReturn(Optional.empty());

		PaymentCancelResponse res = service.cancelPayment(
			MEMBER_ID, ORDER_ID, 1000, CancelReason.USER_REQUEST
		);

		assertEquals(PayStatus.ABORTED.name(), res.getStatus());

		then(paymentRepository).should().save(argThat(p ->
			p.getPayStatus() == PayStatus.ABORTED &&
				p.getBillingKey() == null
		));
		then(historyRepository).should().save(any(PaymentHistory.class));

		// ✅ 해지 예약 알림
		then(notificationProducer).should().cancelScheduled(MEMBER_ID, ORDER_ID);
	}

	@Test
	@DisplayName("cancelPayment(구독): 7일 이내이나 paymentKey 없음 → PAYMENT_CANCEL_ERROR (알림 없음)")
	void cancelPayment_subscription_within7days_missingPaymentKey() {
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			null, "bKey", "cust_" + MEMBER_ID, 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		given(historyRepository.findLastByPaymentIdAndStatuses(any(), anyList()))
			.willReturn(Optional.of(PaymentHistory.create(1L, PayStatus.DONE, "결제 완료")));

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.cancelPayment(MEMBER_ID, ORDER_ID, 500, CancelReason.USER_REQUEST)
		);
		assertEquals(ErrorCode.PAYMENT_CANCEL_ERROR, ex.getErrorCode());

		// ❌ 알림 없음
		then(notificationProducer).should(never()).cancelled(anyLong(), anyString(), anyInt());
		then(notificationProducer).should(never()).cancelScheduled(anyLong(), anyString());
	}

	@Test
	@DisplayName("cancelPayment(구독): 7일 이내 SAGA 실패 → PAYMENT_CANCEL_ERROR로 래핑 (알림 없음)")
	void cancelPayment_subscription_within7days_sagaThrows_wrapAsAppEx() {
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			"pKey-1", "bKey", "cust_" + MEMBER_ID, 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));
		given(historyRepository.findLastByPaymentIdAndStatuses(any(), anyList()))
			.willReturn(Optional.of(PaymentHistory.create(1L, PayStatus.DONE, "결제 완료")));

		willThrow(new RuntimeException("saga-fail")).given(paymentSaga)
			.cancelWithCompensation(eq("pKey-1"), eq(ORDER_ID), eq(3000), eq(CancelReason.USER_REQUEST));

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.cancelPayment(MEMBER_ID, ORDER_ID, 1, CancelReason.USER_REQUEST)
		);
		assertEquals(ErrorCode.PAYMENT_CANCEL_ERROR, ex.getErrorCode());

		// ❌ 알림 없음
		then(notificationProducer).should(never()).cancelled(anyLong(), anyString(), anyInt());
	}

	@Test
	@DisplayName("cancelPayment(원타임): paymentKey로 취소 호출되고 요청 금액 사용 + 취소 알림")
	void cancelPayment_oneTime_success() {
		Plan mockPlan = mock(Plan.class);
		given(mockPlan.isAutoRenewal()).willReturn(false);
		given(planRepository.findById(PLAN_ID)).willReturn(Optional.of(mockPlan));

		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			"pKey-ot", null, "cust_" + MEMBER_ID, 9000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentCancelResponse dummy = new PaymentCancelResponse(77L, "CANCELLED");
		given(paymentSaga.cancelWithCompensation("pKey-ot", ORDER_ID, 1234, CancelReason.USER_REQUEST))
			.willReturn(dummy);

		PaymentCancelResponse res = service.cancelPayment(MEMBER_ID, ORDER_ID, 1234, CancelReason.USER_REQUEST);

		assertEquals(dummy, res);
		then(paymentSaga).should().cancelWithCompensation("pKey-ot", ORDER_ID, 1234, CancelReason.USER_REQUEST);

		// ✅ 취소 완료 알림
		then(notificationProducer).should().cancelled(MEMBER_ID, ORDER_ID, 1234);
	}

	@Test
	@DisplayName("cancelPayment(원타임): paymentKey 없음 → PAYMENT_CANCEL_ERROR (알림 없음)")
	void cancelPayment_oneTime_missingPaymentKey_throws() {
		Plan mockPlan = mock(Plan.class);
		given(mockPlan.isAutoRenewal()).willReturn(false);
		given(planRepository.findById(PLAN_ID)).willReturn(Optional.of(mockPlan));

		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			null, null, "cust_" + MEMBER_ID, 9000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.cancelPayment(MEMBER_ID, ORDER_ID, 500, CancelReason.USER_REQUEST)
		);
		assertEquals(ErrorCode.PAYMENT_CANCEL_ERROR, ex.getErrorCode());

		// ❌ 알림 없음
		then(notificationProducer).should(never()).cancelled(anyLong(), anyString(), anyInt());
	}

	@Test
	@DisplayName("issueBillingKey: SAGA 실패 시 BILLING_ISSUE_ERROR (알림 없음)")
	void issueBillingKey_sagaFail_wrapsAsAppEx() {
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			null, null, "cust_" + MEMBER_ID, 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentIssueBillingKeyParam param = PaymentIssueBillingKeyParam.builder()
			.orderId(ORDER_ID).authKey("auth").customerKey("custKey").build();

		willThrow(new RuntimeException("saga-fail"))
			.given(paymentSaga).issueKeyWithCompensation(param);

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.issueBillingKey(MEMBER_ID, param)
		);
		assertEquals(ErrorCode.BILLING_ISSUE_ERROR, ex.getErrorCode());

		// ❌ 알림 없음
		then(notificationProducer).should(never()).billingKeyIssued(anyLong(), anyString());
	}

	@Test
	@DisplayName("chargeWithBillingKey: SAGA 실패 시 AUTO_CHARGE_ERROR (알림 없음)")
	void chargeWithBillingKey_sagaFail_wrapsAsAppEx() {
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			null, "bKey", "cust_" + MEMBER_ID, 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentAutoChargeParam param = PaymentAutoChargeParam.builder()
			.billingKey("bKey").customerKey("cust_" + MEMBER_ID).amount(3000)
			.orderId(ORDER_ID).orderName("name").customerEmail("email").customerName("name").build();

		willThrow(new RuntimeException("saga-fail"))
			.given(paymentSaga).autoChargeWithCompensation(param, "idem-1");

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.chargeWithBillingKey(MEMBER_ID, param, "idem-1")
		);
		assertEquals(ErrorCode.AUTO_CHARGE_ERROR, ex.getErrorCode());

		// ❌ 알림 없음
		then(notificationProducer).should(never()).autoBillingApproved(anyLong(), anyString(), anyInt());
	}

	@Test
	@DisplayName("confirmPayment: 비구독 플랜이면 구독 갱신 기록 호출 안 함")
	void confirmPayment_nonSubscription_noRenewalRecord() {
		MemberInfoResponse profile = new MemberInfoResponse(1L, "t@e.com", "T");
		given(memberClient.getMyInfo(MEMBER_ID)).willReturn(new RsData<>("200", "OK", profile));

		given(paymentSaga.confirmWithCompensation(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
			.willReturn(501L);

		Payment paid = Payment.create(MEMBER_ID, PLAN_ID, ORDER_ID, null, null, "cust_" + MEMBER_ID, 1000L, "CARD");
		given(paymentRepository.findById(501L)).willReturn(Optional.of(paid));

		Plan mockPlan = mock(Plan.class);
		given(mockPlan.isAutoRenewal()).willReturn(false);
		given(planRepository.findById(PLAN_ID)).willReturn(Optional.of(mockPlan));

		service.confirmPayment(MEMBER_ID, "pKey", ORDER_ID, 1000, "idem");

		then(subscriptionService).should(never()).recordSubscriptionRenewal(anyLong(), any());

		// 승인 자체는 성공했으니 알림은 발행됨
		then(notificationProducer).should().paymentApproved(MEMBER_ID, ORDER_ID, 1000);
	}

	@Test
	@DisplayName("confirmPayment: 결제는 승인됐으나 Plan 조회 실패 시 PAYMENT_CONFIRM_ERROR(현재 동작 기준)")
	void confirmPayment_planNotFound_throws() {
		MemberInfoResponse profile = new MemberInfoResponse(1L, "t@e.com", "T");
		given(memberClient.getMyInfo(MEMBER_ID)).willReturn(new RsData<>("200", "OK", profile));

		given(paymentSaga.confirmWithCompensation(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
			.willReturn(777L);

		Payment paid = Payment.create(MEMBER_ID, PLAN_ID, ORDER_ID, null, null, "cust_" + MEMBER_ID, 1000L, "CARD");
		given(paymentRepository.findById(777L)).willReturn(Optional.of(paid));

		given(planRepository.findById(PLAN_ID)).willReturn(Optional.empty());

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.confirmPayment(MEMBER_ID, "pKey", ORDER_ID, 1000, "idem")
		);
		assertEquals(ErrorCode.PAYMENT_CONFIRM_ERROR, ex.getErrorCode());

		then(notificationProducer).should().paymentApproved(MEMBER_ID, ORDER_ID, 1000);
	}

	@Test
	@DisplayName("expireIfReady: READY가 아니면 스킵 (저장/이력 없음)")
	void expireIfReady_skipWhenNotReady() {
		Payment notReady = Payment.of(
			9L, MEMBER_ID, PLAN_ID, ORDER_ID,
			null, null, "cust_" + MEMBER_ID,
			5000L, PayStatus.DONE,
			"CARD", null, null
		);
		given(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(notReady));

		service.expireIfReady(MEMBER_ID, ORDER_ID);

		then(paymentRepository).should(never()).save(any());
		then(historyRepository).should(never()).save(any());

		then(notificationProducer).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("testTransitionToReady: 성공 → billingKey 등록, 저장 및 이력 기록")
	void testTransitionToReady_success() {
		Payment origin = Payment.create(MEMBER_ID, PLAN_ID, ORDER_ID, null, null, "cust_" + MEMBER_ID, 1000L, "CARD");
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(origin));
		given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

		service.testTransitionToReady(ORDER_ID, "bKey-xyz");

		then(paymentRepository).should().save(argThat(p ->
			PayStatus.AUTO_BILLING_READY == p.getPayStatus() &&
				"bKey-xyz".equals(p.getBillingKey())
		));
		then(historyRepository).should().save(any(PaymentHistory.class));

		then(notificationProducer).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("testTransitionToReady: 주문 없음 → ORDER_NOT_FOUND")
	void testTransitionToReady_orderNotFound_throws() {
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.testTransitionToReady(ORDER_ID, "bKey")
		);
		assertEquals(ErrorCode.ORDER_NOT_FOUND, ex.getErrorCode());

		then(notificationProducer).shouldHaveNoInteractions();
	}
}