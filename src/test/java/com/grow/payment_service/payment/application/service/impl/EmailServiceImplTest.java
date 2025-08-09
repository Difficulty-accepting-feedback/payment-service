package com.grow.payment_service.payment.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

	@Mock
	JavaMailSender mailSender;

	@InjectMocks
	EmailServiceImpl service;

	private MimeMessage newMessage() {
		return new JavaMailSenderImpl().createMimeMessage();
	}

	@BeforeEach
	void setUp() {
		// @Value 주입 필드 셋업
		ReflectionTestUtils.setField(service, "fromAddress", "no-reply@grow.com");
	}

	@Test
	@DisplayName("성공 메일: 필수 정보와 전표 링크가 포함된다")
	void sendPaymentSuccess_includesAllFields() throws Exception {
		when(mailSender.createMimeMessage()).thenReturn(newMessage());
		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);

		String to = "user@grow.com";
		String name = "테스터";
		String orderId = "2025080910004";
		Integer amount = 10000;
		String receiptUrl = "https://receipt.example/tx";
		String method = "간편결제";
		String provider = "카카오페이";
		String requestedAt = "2025-08-09T01:32:30";
		String approvedAt  = "2025-08-09T01:34:02";
		String currency = "KRW";

		service.sendPaymentSuccess(
			to, name, orderId, amount, receiptUrl, method, provider, requestedAt, approvedAt, currency
		);

		verify(mailSender).send(captor.capture());
		MimeMessage msg = captor.getValue();

		assertEquals("결제 완료 안내", msg.getSubject());
		assertEquals(1, msg.getAllRecipients().length);
		assertEquals(to, msg.getAllRecipients()[0].toString());

		String html = (String) msg.getContent();
		assertTrue(html.contains("GROW"));
		assertTrue(html.contains("GROW Plan #"+orderId));
		assertTrue(html.contains("결제금액"));
		assertTrue(html.contains("10,000원"));
		assertTrue(html.contains("결제수단"));
		assertTrue(html.contains("카카오페이 - 간편결제"));
		assertTrue(html.contains("결제일시"));
		assertTrue(html.contains("2025-08-09 01:34:02")); // 'T' → ' '
		assertTrue(html.contains("주문번호"));
		assertTrue(html.contains("매출전표 보기"));
		assertTrue(html.contains(receiptUrl));
	}

	@Test
	@DisplayName("실패 메일: 전표 링크 없이 본문만 생성된다")
	void sendPaymentFailure_withoutReceiptUrl() throws Exception {
		when(mailSender.createMimeMessage()).thenReturn(newMessage());
		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);

		service.sendPaymentFailure(
			"user@grow.com", "테스터", "OID1", 12345,
			null, "간편결제", "토스페이", "2025-08-09T00:00:00", "2025-08-09T00:01:00", "KRW"
		);

		verify(mailSender).send(captor.capture());
		MimeMessage msg = captor.getValue();
		assertEquals("결제 실패 안내", msg.getSubject());

		String html = (String) msg.getContent();
		assertTrue(html.contains("시도 금액"));
		assertTrue(html.contains("12,345원"));
		assertTrue(html.contains("주문번호"));
		assertFalse(html.contains("매출전표 보기")); // 링크 없음
	}

	@Test
	@DisplayName("취소 메일: 취소 금액/사유/취소일시가 포함된다")
	void sendCancellation_includesCancelDetails() throws Exception {
		when(mailSender.createMimeMessage()).thenReturn(newMessage());
		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);

		service.sendCancellation(
			"user@grow.com", "테스터", "OID2", 10000,
			"https://receipt.example/cancel", "카드", "비자",
			"2025-08-09T01:41:20", "2025-08-09T01:43:04",
			"USER_REQUEST", 10000, "KRW"
		);

		verify(mailSender).send(captor.capture());
		MimeMessage msg = captor.getValue();
		assertEquals("결제 취소 안내", msg.getSubject());

		String html = (String) msg.getContent();
		assertTrue(html.contains("취소 금액"));
		assertTrue(html.contains("10,000원"));
		assertTrue(html.contains("취소 사유"));
		assertTrue(html.contains("USER_REQUEST"));
		assertTrue(html.contains("취소일시"));
		assertTrue(html.contains("2025-08-09 01:43:04"));
		assertTrue(html.contains("매출전표 보기"));
	}

	@Test
	@DisplayName("경계값: 일부 필드가 null이어도 메일은 전송된다(필수는 유효값)")
	void nullSafety_doesNotThrow_butRequiresMandatoryFields() throws Exception {
		when(mailSender.createMimeMessage()).thenReturn(newMessage());
		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);

		// 구현은 amount/approvedAt에 null 안전하지 않으므로 필수값은 채움
		service.sendPaymentSuccess(
			"user@grow.com", "테스터", "OID3",
			0,               // amount: 0원
			null,            // receiptUrl: null → 버튼 없음
			null,            // method
			null,            // easyPayProvider
			null,            // requestedAt
			"2025-01-01T00:00:00", // approvedAt: 필수 (replace 사용)
			null             // currency
		);

		verify(mailSender).send(captor.capture());
		String html = (String) captor.getValue().getContent();

		assertTrue(html.contains("구매상품"));
		assertTrue(html.contains("주문번호"));
		assertTrue(html.contains("결제일시"));
		assertTrue(html.contains("2025-01-01 00:00:00"));
		assertFalse(html.contains("매출전표 보기")); // receiptUrl=null 이므로 버튼 없음
	}
}