package com.grow.payment_service.payment.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.grow.payment_service.payment.application.dto.*;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.repository.PaymentHistoryRepository;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;
import com.grow.payment_service.payment.domain.service.OrderIdGenerator;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.global.exception.PaymentApplicationException;
import com.grow.payment_service.payment.saga.PaymentSagaOrchestrator;

@ExtendWith(MockitoExtension.class)
class PaymentApplicationServiceImplTest {

	@Mock PaymentGatewayPort gatewayPort;
	@Mock
	PaymentPersistenceService persistenceService;
	@Mock OrderIdGenerator orderIdGenerator;
	@Mock PaymentRepository paymentRepository;
	@Mock PaymentHistoryRepository historyRepository;
	@Mock PaymentSagaOrchestrator paymentSaga;

	@InjectMocks
	PaymentApplicationServiceImpl service;

	@Test
	@DisplayName("initPaymentData: 정상 흐름")
	void initPaymentData_success() {
		// given
		given(orderIdGenerator.generate(1L)).willReturn("order-123");
		Payment dummy = Payment.create(1L, 2L, "order-123", null, null, "cust_1", 100L, "CARD");
		given(paymentRepository.save(any())).willReturn(dummy);

		// when
		PaymentInitResponse resp = service.initPaymentData(1L, 2L, 100);

		// then
		assertEquals("order-123", resp.getOrderId());
		assertEquals(100, resp.getAmount());
		assertTrue(resp.getOrderName().contains("GROW Plan #order-123"));
		then(paymentRepository).should().save(any(Payment.class));
		then(historyRepository).should().save(any());
	}

	@Test
	@DisplayName("initPaymentData: 저장 중 예외 발생 → PaymentApplicationException")
	void initPaymentData_failure() {
		// given
		given(orderIdGenerator.generate(1L)).willReturn("order-123");
		given(paymentRepository.save(any())).willThrow(new RuntimeException("DB error"));

		// when & then
		assertThrows(PaymentApplicationException.class, () ->
			service.initPaymentData(1L, 2L, 100)
		);
	}

	@Test
	@DisplayName("confirmPayment: SAGA 정상 호출")
	void confirmPayment_success() {
		// given
		given(paymentSaga.confirmWithCompensation("pKey","order-1", 200, "idem-1"))
			.willReturn(42L);

		// when
		Long result = service.confirmPayment("pKey", "order-1", 200, "idem-1");

		// then
		assertEquals(42L, result);
		then(paymentSaga).should().confirmWithCompensation("pKey","order-1",200,"idem-1");
	}

	@Test
	@DisplayName("confirmPayment: SAGA 예외 발생 → PaymentApplicationException")
	void confirmPayment_failure() {
		// given
		given(paymentSaga.confirmWithCompensation(any(),any(),anyInt(),any()))
			.willThrow(new RuntimeException("oops"));

		// when & then
		assertThrows(PaymentApplicationException.class, () ->
			service.confirmPayment("pKey","order-1",200,"idem-1")
		);
	}

	// cancelPayment, issueBillingKey, chargeWithBillingKey 도 동일한 패턴으로 추가 테스트 가능
}