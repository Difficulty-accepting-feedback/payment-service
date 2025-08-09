package com.grow.payment_service.payment.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.payment.application.service.EmailService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.infra.client.MemberClient;
import com.grow.payment_service.payment.infra.client.MemberInfoResponse;
import com.grow.payment_service.payment.presentation.dto.WebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

	@InjectMocks
	private WebhookService service;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private MemberClient memberClient;

	@Mock
	private EmailService emailService;

	private final ObjectMapper om = new ObjectMapper();

	private static final Long MEMBER_ID = 1L;
	private static final Long PLAN_ID = 9L;
	private static final String ORDER_ID = "2025080910006";

	@BeforeEach
	void setUp() {
		Payment payment = Payment.create(
			MEMBER_ID, PLAN_ID, ORDER_ID, null, null, "cust_"+MEMBER_ID, 10_000L, "CARD"
		);
		lenient().when(paymentRepository.findByOrderId(ORDER_ID))
			.thenReturn(Optional.of(payment));

		@SuppressWarnings("unchecked")
		RsData<MemberInfoResponse> rs = mock(RsData.class);
		MemberInfoResponse profile = mock(MemberInfoResponse.class);
		lenient().when(profile.getEmail()).thenReturn("user@grow.com");
		lenient().when(profile.getNickname()).thenReturn("Tester");
		lenient().when(rs.getData()).thenReturn(profile);
		lenient().when(memberClient.getMyInfo(MEMBER_ID)).thenReturn(rs);
	}

	@Test
	@DisplayName("결제 완료(DONE): 이메일 성공 안내 발송")
	void onPaymentStatusChanged_done_sendsSuccess() throws Exception {
		String json = """
        {
          "eventType":"PAYMENT_STATUS_CHANGED",
          "createdAt":"2025-08-09T01:34:02.379897",
          "data":{
            "paymentKey":"tviva20250809013230Aipf7",
            "orderId":"%s",
            "orderName":"GROW Plan #%s",
            "status":"DONE",
            "requestedAt":"2025-08-09T01:32:30+09:00",
            "approvedAt":"2025-08-09T01:34:02+09:00",
            "method":"간편결제",
            "easyPay":{"provider":"카카오페이","amount":10000,"discountAmount":0},
            "currency":"KRW",
            "totalAmount":10000,
            "receipt":{"url":"https://dashboard.tosspayments.com/receipt/redirection?tx=ok"}
          }
        }
        """.formatted(ORDER_ID, ORDER_ID);

		WebhookRequest req = om.readValue(json, WebhookRequest.class);

		service.onPaymentStatusChanged(req.getData());

		verify(emailService, times(1)).sendPaymentSuccess(
			eq("user@grow.com"),
			eq("Tester"),
			eq(ORDER_ID),
			eq(10000),
			eq("https://dashboard.tosspayments.com/receipt/redirection?tx=ok"),
			eq("간편결제"),
			eq("카카오페이"),
			eq("2025-08-09T01:32:30+09:00"),
			eq("2025-08-09T01:34:02+09:00"),
			eq("KRW")
		);
		verifyNoMoreInteractions(emailService);
	}

	@Test
	@DisplayName("결제 실패(FAILED): 이메일 실패 안내 발송")
	void onPaymentStatusChanged_failed_sendsFailure() throws Exception {
		String json = """
        {
          "eventType":"PAYMENT_STATUS_CHANGED",
          "createdAt":"2025-08-09T01:34:02.379897",
          "data":{
            "paymentKey":"pk",
            "orderId":"%s",
            "orderName":"GROW Plan #%s",
            "status":"FAILED",
            "requestedAt":"2025-08-09T01:32:30+09:00",
            "approvedAt":"2025-08-09T01:34:02+09:00",
            "method":"간편결제",
            "easyPay":{"provider":"토스페이","amount":10000,"discountAmount":0},
            "currency":"KRW",
            "totalAmount":10000,
            "receipt":{"url":"https://receipt/failed"}
          }
        }
        """.formatted(ORDER_ID, ORDER_ID);

		WebhookRequest req = om.readValue(json, WebhookRequest.class);

		service.onPaymentStatusChanged(req.getData());

		verify(emailService, times(1)).sendPaymentFailure(
			eq("user@grow.com"),
			eq("Tester"),
			eq(ORDER_ID),
			eq(10000),
			eq("https://receipt/failed"),
			eq("간편결제"),
			eq("토스페이"),
			eq("2025-08-09T01:32:30+09:00"),
			eq("2025-08-09T01:34:02+09:00"),
			eq("KRW")
		);
		verifyNoMoreInteractions(emailService);
	}

	@Test
	@DisplayName("결제 진행중(IN_PROGRESS): 무시하고 메일 발송 안함")
	void onPaymentStatusChanged_ignored() throws Exception {
		String json = """
        { "eventType":"PAYMENT_STATUS_CHANGED",
          "data":{
            "orderId":"%s",
            "status":"IN_PROGRESS",
            "totalAmount":10000
          }
        }""".formatted(ORDER_ID);

		WebhookRequest req = om.readValue(json, WebhookRequest.class);

		service.onPaymentStatusChanged(req.getData());

		verifyNoInteractions(emailService);
	}

	@Test
	@DisplayName("취소(CANCELED): 취소 사유/금액 포함하여 메일 발송")
	void onCancelStatusChanged_canceled_sendsCancelMail() throws Exception {
		String json = """
        {
          "eventType":"PAYMENT_STATUS_CHANGED",
          "data":{
            "paymentKey":"tviva20250809014120szRX5",
            "orderId":"%s",
            "orderName":"GROW Plan #%s",
            "status":"CANCELED",
            "requestedAt":"2025-08-09T01:41:20+09:00",
            "approvedAt":"2025-08-09T01:43:04+09:00",
            "method":"카드",
            "easyPay":{"provider":"비자"},
            "currency":"KRW",
            "totalAmount":10000,
            "receipt":{"url":"https://receipt/cancel"},
            "cancels":[{"cancelReason":"USER_REQUEST","cancelAmount":10000}]
          }
        }
        """.formatted(ORDER_ID, ORDER_ID);

		WebhookRequest req = om.readValue(json, WebhookRequest.class);

		service.onCancelStatusChanged(req.getData());

		verify(emailService, times(1)).sendCancellation(
			eq("user@grow.com"),
			eq("Tester"),
			eq(ORDER_ID),
			eq(10000),
			eq("https://receipt/cancel"),
			eq("카드"),
			eq("비자"),
			eq("2025-08-09T01:41:20+09:00"),
			eq("2025-08-09T01:43:04+09:00"),
			eq("USER_REQUEST"),
			eq(10000),
			eq("KRW")
		);
		verifyNoMoreInteractions(emailService);
	}

	@Test
	@DisplayName("취소 상태 아님: 메일 발송 안함")
	void onCancelStatusChanged_notCanceled_ignored() throws Exception {
		String json = """
        { "eventType":"PAYMENT_STATUS_CHANGED",
          "data":{
            "orderId":"%s",
            "status":"DONE",
            "totalAmount":10000
          }
        }""".formatted(ORDER_ID);

		WebhookRequest req = om.readValue(json, WebhookRequest.class);

		service.onCancelStatusChanged(req.getData());

		verifyNoInteractions(emailService);
	}

	@Test
	@DisplayName("이메일 없음: 경고 로그 후 발송하지 않음")
	void onPaymentStatusChanged_emailMissing_noSend() throws Exception {
		// memberClient가 빈 이메일 반환하도록 재정의
		@SuppressWarnings("unchecked")
		RsData<MemberInfoResponse> rs = mock(RsData.class);
		MemberInfoResponse profile = mock(MemberInfoResponse.class);
		when(profile.getEmail()).thenReturn("  ");  // blank
		when(profile.getNickname()).thenReturn("Tester");
		when(rs.getData()).thenReturn(profile);
		when(memberClient.getMyInfo(MEMBER_ID)).thenReturn(rs);

		String json = """
        { "eventType":"PAYMENT_STATUS_CHANGED",
          "data":{
            "orderId":"%s",
            "status":"DONE",
            "totalAmount":10000
          }
        }""".formatted(ORDER_ID);

		WebhookRequest req = om.readValue(json, WebhookRequest.class);

		service.onPaymentStatusChanged(req.getData());

		verifyNoInteractions(emailService);
	}
}