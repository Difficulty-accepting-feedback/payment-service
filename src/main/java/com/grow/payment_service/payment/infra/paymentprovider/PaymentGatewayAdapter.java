package com.grow.payment_service.payment.infra.paymentprovider;

import org.springframework.stereotype.Component;

import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;


import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentGatewayAdapter implements PaymentGatewayPort {

	private final TossPaymentClient tossClient;

	@Override
	public void confirmPayment(String paymentKey, String orderId, int amount) {
		tossClient.confirmPayment(paymentKey, orderId, amount);
	}

	@Override
	public TossCancelResponse cancelPayment(String paymentKey, String reason, int amount, String message) {
		return tossClient.cancelPayment(paymentKey, reason, amount, message);
	}

	@Override
	public TossBillingAuthResponse issueBillingKey(String authKey, String customerKey) {
		return tossClient.issueBillingKey(authKey, customerKey);
	}

	@Override
	public TossBillingChargeResponse chargeWithBillingKey(
		String billingKey,
		String customerKey,
		int amount,
		String orderId,
		String orderName,
		String customerEmail,
		String customerName,
		int taxFreeAmount,
		int taxExemptionAmount
	) {
		return tossClient.chargeWithBillingKey(
			billingKey,
			customerKey,
			amount,
			orderId,
			orderName,
			customerEmail,
			customerName,
			taxFreeAmount,
			taxExemptionAmount
		);
	}
}