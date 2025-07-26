package com.grow.payment_service.payment.application.service;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentInitResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;

public interface PaymentApplicationService {
	PaymentInitResponse initPaymentData(Long memberId, Long planId, int amount);
	Long confirmPayment(String paymentKey, String orderIdStr, int amount);
	PaymentCancelResponse cancelPayment(String paymentKey, String orderIdStr, int cancelAmount, CancelReason reason);
	PaymentIssueBillingKeyResponse issueBillingKey(PaymentIssueBillingKeyParam param);
	PaymentConfirmResponse chargeWithBillingKey(PaymentAutoChargeParam param);
}