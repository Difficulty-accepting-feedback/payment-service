package com.grow.payment_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.payment.application.service.PaymentBatchService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;

@SpringBootTest
@Transactional
class PaymentBillingKeyRemovalTest {

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentBatchService schedulerService;

	@Test
	void billingKey_remove_test() {
		Payment p = Payment.create(1L, 1L, "order-1", null, "BK-ABC", "cust_1", 1000L, "CARD");
}