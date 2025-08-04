package com.grow.payment_service.payment.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.grow.payment_service.payment.application.dto.PaymentDetailResponse;
import com.grow.payment_service.payment.application.dto.PaymentHistoryResponse;
import com.grow.payment_service.payment.global.exception.PaymentApplicationException;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceImplTest {

	@Mock PaymentRepository paymentRepository;
	@Mock PaymentHistoryRepository historyRepository;
	@InjectMocks PaymentQueryServiceImpl queryService;

	private Payment makePayment(Long id) {
		return Payment.of(
			id,
			10L, // memberId
			20L, // planId
			"ord-" + id,
			"payKey",
			"billKey",
			"cust",
			1000L,
			PayStatus.DONE,
			"CARD",
			null,
			null
		);
	}

	private PaymentHistory makeHistory(Long histId, Long paymentId, PayStatus status, String reason) {
		// createdAt 를 고정하기 위해 of() 사용
		return PaymentHistory.of(
			histId,
			paymentId,
			status,
			LocalDateTime.of(2025, 8, 1, 12, 0),
			reason
		);
	}

	@Test
	@DisplayName("getPayment: 정상 조회")
	void getPayment_success() {
		// given
		Payment p = makePayment(1L);
		List<PaymentHistory> histList = List.of(
			makeHistory(101L, 1L, PayStatus.READY, "init"),
			makeHistory(102L, 1L, PayStatus.DONE, "complete")
		);
		given(paymentRepository.findById(1L)).willReturn(Optional.of(p));
		given(historyRepository.findByPaymentId(1L)).willReturn(histList);

		// when
		PaymentDetailResponse dto = queryService.getPayment(1L);

		// then
		assertEquals(1L, dto.getPaymentId());
		assertEquals(10L, dto.getMemberId());
		assertEquals("ord-1", dto.getOrderId());
		assertEquals("DONE", dto.getPayStatus());
		List<PaymentHistoryResponse> hr = dto.getHistory();
		assertEquals(2, hr.size());
		assertEquals("READY", hr.get(0).getStatus());
		assertEquals("init", hr.get(0).getReasonDetail());
		assertEquals("DONE", hr.get(1).getStatus());
		assertEquals("complete", hr.get(1).getReasonDetail());
	}

	@Test
	@DisplayName("getPayment: 존재하지 않으면 예외")
	void getPayment_notFound() {
		given(paymentRepository.findById(2L)).willReturn(Optional.empty());

		assertThrows(PaymentApplicationException.class, () -> queryService.getPayment(2L));
	}

	@Test
	@DisplayName("getPaymentsByMemberId: 여러 건 조회")
	void getPaymentsByMemberId_success() {
		// given
		Payment p1 = makePayment(1L);
		Payment p2 = makePayment(2L);
		List<Payment> payments = List.of(p1, p2);

		List<PaymentHistory> hist1 = List.of(
			makeHistory(201L, 1L, PayStatus.READY, "h1")
		);
		List<PaymentHistory> hist2 = List.of(
			makeHistory(202L, 2L, PayStatus.DONE, "h2")
		);

		given(paymentRepository.findAllByMemberId(10L)).willReturn(payments);
		given(historyRepository.findByPaymentId(1L)).willReturn(hist1);
		given(historyRepository.findByPaymentId(2L)).willReturn(hist2);

		// when
		List<PaymentDetailResponse> list = queryService.getPaymentsByMemberId(10L);

		// then
		assertEquals(2, list.size());
		var dto1 = list.get(0);
		assertEquals(1L, dto1.getPaymentId());
		assertEquals(1, dto1.getHistory().size());
		assertEquals("h1", dto1.getHistory().get(0).getReasonDetail());

		var dto2 = list.get(1);
		assertEquals(2L, dto2.getPaymentId());
		assertEquals(1, dto2.getHistory().size());
		assertEquals("h2", dto2.getHistory().get(0).getReasonDetail());
	}

	@Test
	@DisplayName("getPaymentsByMemberId: 조회 결과 없으면 빈 리스트")
	void getPaymentsByMemberId_empty() {
		given(paymentRepository.findAllByMemberId(99L)).willReturn(List.of());

		List<PaymentDetailResponse> list = queryService.getPaymentsByMemberId(99L);

		assertTrue(list.isEmpty());
	}
}