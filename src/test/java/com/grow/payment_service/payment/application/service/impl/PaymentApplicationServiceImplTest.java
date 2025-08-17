package com.grow.payment_service.payment.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.ArgumentMatchers.*;

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
	@Mock private PaymentHistoryRepository historyRepository;
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
		// 공통: 월간 구독 플랜 리턴
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
		// 1) 멤버 정보 stub
		MemberInfoResponse profile = new MemberInfoResponse(1L, "test@example.com", "TestUser");
		given(memberClient.getMyInfo(MEMBER_ID))
			.willReturn(new RsData<>("200", "OK", profile));

		// 2) SAGA 호출 stub (email, name 포함)
		given(paymentSaga.confirmWithCompensation(
			"pKey", ORDER_ID, 1234, "idem", "test@example.com", "TestUser"
		)).willReturn(100L);

		// 3) DB 조회 stub
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			null, null, "cust_" + MEMBER_ID, 1234L, "CARD"
		);
		given(paymentRepository.findById(100L)).willReturn(Optional.of(paid));

		// when
		Long result = service.confirmPayment(
			MEMBER_ID, "pKey", ORDER_ID, 1234, "idem"
		);

		// then
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

		// DB 조회시 다른 멤버
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
	@DisplayName("cancelPayment: 정상 호출(서버가 DB의 paymentKey를 찾아 SAGA 호출)")
	void cancelPayment_success() {
		// DB에 paymentKey 저장되어 있다고 가정
		Payment paid = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID,
			"pKey-1", null, "cust_" + MEMBER_ID, 3000L, "CARD"
		);
		given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(paid));

		PaymentCancelResponse dummyRes = new PaymentCancelResponse(123L, "CANCELLED");
		// SAGA는 서버에서 조회한 paymentKey로 호출되어야 함
		given(paymentSaga.cancelWithCompensation(
			"pKey-1", ORDER_ID, 1000, CancelReason.USER_REQUEST
		)).willReturn(dummyRes);

		PaymentCancelResponse res = service.cancelPayment(
			MEMBER_ID, ORDER_ID, 1000, CancelReason.USER_REQUEST
		);

		assertEquals(dummyRes, res);
		then(paymentSaga).should().cancelWithCompensation(
			"pKey-1", ORDER_ID, 1000, CancelReason.USER_REQUEST
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
		PaymentConfirmResponse dummy = new PaymentConfirmResponse(555L, "APPROVED", "email", "name");
		given(paymentSaga.autoChargeWithCompensation(param, "idem")).willReturn(dummy);

		PaymentConfirmResponse res = service.chargeWithBillingKey(MEMBER_ID, param, "idem");

		assertEquals("APPROVED", res.getPayStatus());
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
}