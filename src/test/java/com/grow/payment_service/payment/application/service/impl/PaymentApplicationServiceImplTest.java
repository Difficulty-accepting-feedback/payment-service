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
import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentInitResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentApplicationServiceImplTest {

	@Mock private PlanRepository planRepository;
	@Mock private OrderIdGenerator orderIdGenerator;
	@Mock private PaymentRepository paymentRepository;
	@Mock private PaymentHistoryRepository historyRepository; // ✅ 사용
	@Mock private PaymentSagaOrchestrator paymentSaga;
	@Mock private SubscriptionHistoryApplicationService subscriptionService;
	@Mock private MemberClient memberClient;

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
	}

	@Test
	@DisplayName("confirmPayment: 멤버 불일치 시 도메인 예외 발생")
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

		PaymentDomainException ex = assertThrows(
			PaymentDomainException.class,
			() -> service.confirmPayment(MEMBER_ID, "pKey", ORDER_ID, 1000, "idem")
		);
		assertTrue(ex.getMessage().contains("memberId=10"));
	}

	@Test
	@DisplayName("confirmPayment: SAGA 예외 시 RuntimeException 그대로 노출")
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
	}

	@Test
	@DisplayName("cancelPayment(구독): 7일 이내 전액 환불 → 서버가 DB의 paymentKey로 SAGA 호출되고 금액은 전체금액")
	void cancelPayment_success() {
		// 결제 엔티티(총액 3000, paymentKey 존재)
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			"pKey-1", null, "cust_" + MEMBER_ID, 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		// 🔹 최근 승인 이력 존재하도록 스텁(=> 7일 이내 환불 경로)
		given(historyRepository.findLastByPaymentIdAndStatuses(any(), anyList()))
			.willReturn(Optional.of(
				PaymentHistory.create(1L, PayStatus.DONE, "결제 완료")
			));

		PaymentCancelResponse dummyRes = new PaymentCancelResponse(123L, "CANCELLED");
		// 🔹 7일 이내 정책 → 전체금액(3000)으로 취소 호출됨
		given(paymentSaga.cancelWithCompensation(
			"pKey-1", ORDER_ID, 3000, CancelReason.USER_REQUEST
		)).willReturn(dummyRes);

		PaymentCancelResponse res = service.cancelPayment(
			MEMBER_ID, ORDER_ID, 1000, CancelReason.USER_REQUEST // ← 요청 금액 1000이더라도 정책상 3000으로 처리
		);

		assertEquals(dummyRes, res);
		then(paymentSaga).should().cancelWithCompensation(
			"pKey-1", ORDER_ID, 3000, CancelReason.USER_REQUEST
		);
	}

	@Test
	@DisplayName("cancelPayment: 멤버 불일치 시 도메인 예외 발생")
	void cancelPayment_memberMismatch() {
		Payment paid = Payment.create(
			999L, PLAN_ID, ORDER_ID,
			"pKey-x", null, "cust_999", 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentDomainException ex = assertThrows(
			PaymentDomainException.class,
			() -> service.cancelPayment(MEMBER_ID, ORDER_ID, 1000, CancelReason.USER_REQUEST)
		);
		assertTrue(ex.getMessage().contains("memberId=10"));
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

		PaymentDomainException ex = assertThrows(
			PaymentDomainException.class,
			() -> service.issueBillingKey(MEMBER_ID, param)
		);
		assertTrue(ex.getMessage().contains("memberId=10"));
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

		// 🔹 상태명 최신화: "AUTO_BILLING_APPROVED"
		PaymentConfirmResponse dummy = new PaymentConfirmResponse(
			555L, "AUTO_BILLING_APPROVED", "email", "name", "paymentKey"
		);
		given(paymentSaga.autoChargeWithCompensation(param, "idem")).willReturn(dummy);

		PaymentConfirmResponse res = service.chargeWithBillingKey(MEMBER_ID, param, "idem");

		assertEquals("AUTO_BILLING_APPROVED", res.getPayStatus());
		then(paymentSaga).should().autoChargeWithCompensation(param, "idem");
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

		PaymentDomainException ex = assertThrows(
			PaymentDomainException.class,
			() -> service.chargeWithBillingKey(MEMBER_ID, param, "idem")
		);
		assertTrue(ex.getMessage().contains("memberId=10"));
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
	}

	@Test
	@DisplayName("expireIfReady: 소유자 불일치 → 도메인 예외 발생 & 저장/이력 없음")
	void expireIfReady_memberMismatch_throws() {
		Payment others = Payment.create(
			999L, PLAN_ID, ORDER_ID,
			null, null, "cust_999", 5000L, "CARD"
		);
		given(paymentRepository.findByOrderIdForUpdate(ORDER_ID))
			.willReturn(Optional.of(others));

		assertThrows(
			PaymentDomainException.class,
			() -> service.expireIfReady(MEMBER_ID, ORDER_ID)
		);

		then(paymentRepository).should().findByOrderIdForUpdate(ORDER_ID);
		then(paymentRepository).should(never()).save(any());
		then(historyRepository).should(never()).save(any());
	}


	@Test
	@DisplayName("cancelPayment(구독): 7일 초과 → 다음 달부터 해지 예약 (billingKey 제거 + ABORTED 저장)")
	void cancelPayment_subscription_after7days_abortAndClearKey() {
		// payment: 현재 월 결제가 이미 승인된 상태라고 가정(AUTO_BILLING_APPROVED)
		Payment paid = Payment.of(
			/*paymentId*/ 1L, MEMBER_ID, PLAN_ID, ORDER_ID,
			/*paymentKey*/ "pKey-1", /*billingKey*/ "bKey-1", /*customerKey*/ "cust_" + MEMBER_ID,
			/*amount*/ 3000L, /*status*/ PayStatus.AUTO_BILLING_APPROVED,
			/*method*/ "CARD", /*failureReason*/ null, /*cancelReason*/ null
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		// 구독 플랜(기본 @BeforeEach 설정 유지) + 최근 승인 이력 없음(= 7일 초과로 간주)
		given(historyRepository.findLastByPaymentIdAndStatuses(any(), anyList()))
			.willReturn(Optional.empty());

		PaymentCancelResponse res = service.cancelPayment(
			MEMBER_ID, ORDER_ID, /*req*/ 1000, CancelReason.USER_REQUEST
		);

		assertEquals(PayStatus.ABORTED.name(), res.getStatus());

		// 저장 시 ABORTED이고 billingKey 제거됐는지 확인
		then(paymentRepository).should().save(argThat(p ->
			p.getPayStatus() == PayStatus.ABORTED &&
				p.getBillingKey() == null
		));
		then(historyRepository).should().save(any(PaymentHistory.class));
	}

	@Test
	@DisplayName("cancelPayment(구독): 7일 이내이나 paymentKey 없음 → PAYMENT_CANCEL_ERROR")
	void cancelPayment_subscription_within7days_missingPaymentKey() {
		// 결제 건에 paymentKey가 비어있음
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			/*paymentKey*/ null, /*billingKey*/ "bKey", "cust_" + MEMBER_ID, 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		// 최근 승인 이력 존재 → 7일 이내 경로
		given(historyRepository.findLastByPaymentIdAndStatuses(any(), anyList()))
			.willReturn(Optional.of(PaymentHistory.create(1L, PayStatus.DONE, "결제 완료")));

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.cancelPayment(MEMBER_ID, ORDER_ID, 500, CancelReason.USER_REQUEST)
		);
		assertEquals(ErrorCode.PAYMENT_CANCEL_ERROR, ex.getErrorCode());
		then(paymentSaga).should(never()).cancelWithCompensation(anyString(), anyString(), anyInt(), any());
	}

	@Test
	@DisplayName("cancelPayment(구독): 7일 이내 SAGA 실패 → PAYMENT_CANCEL_ERROR로 래핑")
	void cancelPayment_subscription_within7days_sagaThrows_wrapAsAppEx() {
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			/*paymentKey*/ "pKey-1", /*billingKey*/ "bKey", "cust_" + MEMBER_ID, 3000L, "CARD"
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
	}

	@Test
	@DisplayName("cancelPayment(원타임): paymentKey로 취소 호출되고 요청 금액 사용")
	void cancelPayment_oneTime_success() {
		// 원타임 플랜으로 오버라이드 (isAutoRenewal() = false)
		Plan mockPlan = mock(Plan.class);
		given(mockPlan.isAutoRenewal()).willReturn(false);
		given(planRepository.findById(PLAN_ID)).willReturn(Optional.of(mockPlan));

		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			/*paymentKey*/ "pKey-ot", /*billingKey*/ null, "cust_" + MEMBER_ID, 9000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentCancelResponse dummy = new PaymentCancelResponse(77L, "CANCELLED");
		given(paymentSaga.cancelWithCompensation("pKey-ot", ORDER_ID, 1234, CancelReason.USER_REQUEST))
			.willReturn(dummy);

		PaymentCancelResponse res = service.cancelPayment(MEMBER_ID, ORDER_ID, 1234, CancelReason.USER_REQUEST);

		assertEquals(dummy, res);
		then(paymentSaga).should().cancelWithCompensation("pKey-ot", ORDER_ID, 1234, CancelReason.USER_REQUEST);
	}

	@Test
	@DisplayName("cancelPayment(원타임): paymentKey 없음 → PAYMENT_CANCEL_ERROR")
	void cancelPayment_oneTime_missingPaymentKey_throws() {
		Plan mockPlan = mock(Plan.class);
		given(mockPlan.isAutoRenewal()).willReturn(false);
		given(planRepository.findById(PLAN_ID)).willReturn(Optional.of(mockPlan));

		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			/*paymentKey*/ null, /*billingKey*/ null, "cust_" + MEMBER_ID, 9000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.cancelPayment(MEMBER_ID, ORDER_ID, 500, CancelReason.USER_REQUEST)
		);
		assertEquals(ErrorCode.PAYMENT_CANCEL_ERROR, ex.getErrorCode());
		then(paymentSaga).should(never()).cancelWithCompensation(anyString(), anyString(), anyInt(), any());
	}

	@Test
	@DisplayName("issueBillingKey: SAGA 실패 시 BILLING_ISSUE_ERROR")
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
	}

	@Test
	@DisplayName("chargeWithBillingKey: SAGA 실패 시 AUTO_CHARGE_ERROR")
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
	}

	@Test
	@DisplayName("confirmPayment: 비구독 플랜이면 구독 갱신 기록 호출 안 함")
	void confirmPayment_nonSubscription_noRenewalRecord() {
		// 멤버 정보
		MemberInfoResponse profile = new MemberInfoResponse(1L, "t@e.com", "T");
		given(memberClient.getMyInfo(MEMBER_ID)).willReturn(new RsData<>("200", "OK", profile));

		// SAGA 승인 성공 → paymentId=501
		given(paymentSaga.confirmWithCompensation(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
			.willReturn(501L);

		// 해당 Payment
		Payment paid = Payment.create(MEMBER_ID, PLAN_ID, ORDER_ID, null, null, "cust_" + MEMBER_ID, 1000L, "CARD");
		given(paymentRepository.findById(501L)).willReturn(Optional.of(paid));

		// 비구독 플랜으로 오버라이드
		Plan mockPlan = mock(Plan.class);
		given(mockPlan.isAutoRenewal()).willReturn(false);
		given(planRepository.findById(PLAN_ID)).willReturn(Optional.of(mockPlan));

		service.confirmPayment(MEMBER_ID, "pKey", ORDER_ID, 1000, "idem");

		then(subscriptionService).should(never()).recordSubscriptionRenewal(anyLong(), any());
	}

	@Test
	@DisplayName("confirmPayment: 결제는 승인됐으나 Plan 조회 실패 시 PAYMENT_INIT_ERROR")
	void confirmPayment_planNotFound_throws() {
		// 멤버 정보
		MemberInfoResponse profile = new MemberInfoResponse(1L, "t@e.com", "T");
		given(memberClient.getMyInfo(MEMBER_ID)).willReturn(new RsData<>("200", "OK", profile));

		// SAGA 승인 성공 → paymentId=777
		given(paymentSaga.confirmWithCompensation(anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
			.willReturn(777L);

		Payment paid = Payment.create(MEMBER_ID, PLAN_ID, ORDER_ID, null, null, "cust_" + MEMBER_ID, 1000L, "CARD");
		given(paymentRepository.findById(777L)).willReturn(Optional.of(paid));

		// Plan 조회 실패
		given(planRepository.findById(PLAN_ID)).willReturn(Optional.empty());

		PaymentApplicationException ex = assertThrows(
			PaymentApplicationException.class,
			() -> service.confirmPayment(MEMBER_ID, "pKey", ORDER_ID, 1000, "idem")
		);
		assertEquals(ErrorCode.PAYMENT_INIT_ERROR, ex.getErrorCode());
	}

	@Test
	@DisplayName("expireIfReady: READY가 아니면 스킵 (저장/이력 없음)")
	void expireIfReady_skipWhenNotReady() {
		Payment notReady = Payment.of(
			/*paymentId*/ 9L, MEMBER_ID, PLAN_ID, ORDER_ID,
			/*paymentKey*/ null, /*billingKey*/ null, "cust_" + MEMBER_ID,
			/*amount*/ 5000L, /*status*/ PayStatus.DONE,
			/*method*/ "CARD", /*failureReason*/ null, /*cancelReason*/ null
		);
		given(paymentRepository.findByOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(notReady));

		service.expireIfReady(MEMBER_ID, ORDER_ID);

		then(paymentRepository).should(never()).save(any());
		then(historyRepository).should(never()).save(any());
	}

	@Test
	@DisplayName("testTransitionToReady: 성공 → billingKey 등록, 저장 및 이력 기록")
	void testTransitionToReady_success() {
		Payment origin = Payment.create(MEMBER_ID, PLAN_ID, ORDER_ID, null, null, "cust_" + MEMBER_ID, 1000L, "CARD");
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(origin));
		given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0)); // 저장되도록

		service.testTransitionToReady(ORDER_ID, "bKey-xyz");

		then(paymentRepository).should().save(argThat(p ->
			PayStatus.AUTO_BILLING_READY == p.getPayStatus() &&
				"bKey-xyz".equals(p.getBillingKey())
		));
		then(historyRepository).should().save(any(PaymentHistory.class));
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
	}
}