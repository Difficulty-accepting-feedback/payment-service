package com.grow.payment_service.payment.infra.paymentprovider;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
@Component
@RequiredArgsConstructor
public class TossPaymentClientImpl implements TossPaymentClient {

	@Value("${toss.secret-key}")
	private String secretKey;

	@Value("${toss.base-url}")
	private String baseUrl;

	private final WebClient.Builder webClientBuilder;

	@Override
	public TossInitResponse initPayment(
		String orderId,
		int amount,
		String orderName,
		String successUrl,
		String failUrl
	) {
		WebClient client = webClientBuilder
			.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodeKey(secretKey))
			.build();

		return client.post()
			.uri("/payments")
			.bodyValue(Map.of(
				"method",    "CARD",
				"orderId",   orderId,
				"amount",    amount,
				"orderName", orderName,
				"successUrl", successUrl,
				"failUrl",    failUrl
			))
			.retrieve()
			.bodyToMono(TossInitResponse.class)
			.block();
	}


	@Override
	public TossPaymentResponse confirmPayment(String paymentKey, String orderId, int amount) {
		WebClient client = webClientBuilder
			.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodeKey(secretKey))
			.build();

		return client.post()
			.uri("/payments/confirm")
			.bodyValue(Map.of(
				"paymentKey", paymentKey,
				"orderId",    orderId,
				"amount",     amount
			))
			.retrieve()
			// HttpStatusCode::isError 로 수정
			.onStatus(HttpStatusCode::isError, resp ->
				resp.bodyToMono(String.class)
					.flatMap(body -> Mono.error(new TossException("토스 승인 실패: " + body)))
			)
			.bodyToMono(TossPaymentResponse.class)
			.block();
	}

	@Override
	public TossCancelResponse cancelPayment(
		String paymentKey,
		String cancelReason,
		int cancelAmount,
		String cancelReasonDetail
	) {
		WebClient client = webClientBuilder
			.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodeKey(secretKey))
			.build();

		return client.post()
			.uri("/payments/{paymentKey}/cancel", paymentKey)
			.bodyValue(Map.of(
				"cancelReason",       cancelReason,
				"cancelAmount",       cancelAmount,
				"cancelReasonDetail", cancelReasonDetail
			))
			.retrieve()
			.onStatus(HttpStatusCode::isError, resp ->
				resp.bodyToMono(String.class)
					.flatMap(body -> Mono.error(new TossException("토스 취소 실패: " + body)))
			)
			.bodyToMono(TossCancelResponse.class)
			.block();
	}

	@Override
	public TossBillingAuthResponse issueBillingKey(String authKey, String customerKey) {
		return webClientBuilder.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodeKey(secretKey))
			.build()
			.post().uri("/billing/authorizations/issue")
			.bodyValue(Map.of("authKey", authKey, "customerKey", customerKey))
			.retrieve().onStatus(HttpStatusCode::isError, resp ->
				resp.bodyToMono(String.class)
					.flatMap(body -> Mono.error(new TossException("빌링키 발급 실패: " + body)))
			)
			.bodyToMono(TossBillingAuthResponse.class).block();
	}

	@Override
	public TossBillingChargeResponse chargeWithBillingKey(
		String billingKey, String customerKey, int amount,
		String orderId, String orderName,
		String customerEmail, String customerName,
		Integer taxFreeAmount, Integer taxExemptionAmount
	) {
		Map<String, Object> body = new HashMap<>();
		body.put("customerKey",    customerKey);
		body.put("amount",         amount);
		body.put("orderId",        orderId);
		body.put("orderName",      orderName);
		body.put("customerEmail",  customerEmail);
		body.put("customerName",   customerName);
		if (taxFreeAmount != null) {
			body.put("taxFreeAmount",      taxFreeAmount);
		}
		if (taxExemptionAmount != null) {
			body.put("taxExemptionAmount", taxExemptionAmount);
		}

		return webClientBuilder
			.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodeKey(secretKey))
			.build()
			.post()
			.uri("/billing/{billingKey}", billingKey)
			.bodyValue(body)
			.retrieve()
			.onStatus(HttpStatusCode::isError, resp ->
				resp.bodyToMono(String.class)
					.flatMap(b -> Mono.error(new TossException("자동결제 승인 실패: " + b)))
			)
			.bodyToMono(TossBillingChargeResponse.class)
			.block();
	}


	private static String encodeKey(String key) {
		return Base64.getEncoder()
			.encodeToString((key + ":").getBytes(StandardCharsets.UTF_8));
	}
}