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

import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.TossException;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingAuthResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingChargeResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossCancelResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossInitResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossPaymentResponse;

import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TossPaymentClientImpl implements TossPaymentClient {

	@Value("${toss.secret-key}")
	private String secretKey;

	@Value("${toss.base-url}")
	private String baseUrl;

	private final WebClient.Builder webClientBuilder;

	/** 결제 초기화 */
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
				"method",     "CARD",
				"orderId",    orderId,
				"amount",     amount,
				"orderName",  orderName,
				"successUrl", successUrl,
				"failUrl",    failUrl
			))
			.retrieve()
			.onStatus(HttpStatusCode::isError, resp ->
				resp.bodyToMono(String.class)
					.flatMap(body -> Mono.error(
						new TossException(ErrorCode.TOSS_API_ERROR, new RuntimeException(body))
					))
			)
			.bodyToMono(TossInitResponse.class)
			.block();
	}

	/** 결제 승인 */
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
			.onStatus(HttpStatusCode::isError, resp ->
				resp.bodyToMono(String.class)
					.flatMap(body -> Mono.error(
						new TossException(ErrorCode.TOSS_API_ERROR, new RuntimeException(body))
					))
			)
			.bodyToMono(TossPaymentResponse.class)
			.block();
	}

	/** 결제 취소 */
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
					.flatMap(body -> Mono.error(
						new TossException(ErrorCode.TOSS_API_ERROR, new RuntimeException(body))
					))
			)
			.bodyToMono(TossCancelResponse.class)
			.block();
	}

	/** 빌링키 발급 */
	@Override
	public TossBillingAuthResponse issueBillingKey(String authKey, String customerKey) {
		return webClientBuilder.baseUrl(baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodeKey(secretKey))
			.build()
			.post().uri("/billing/authorizations/issue")
			.bodyValue(Map.of("authKey", authKey, "customerKey", customerKey))
			.retrieve()
			.onStatus(HttpStatusCode::isError, resp ->
				resp.bodyToMono(String.class)
					.flatMap(body -> Mono.error(
						new TossException(ErrorCode.TOSS_API_ERROR, new RuntimeException(body))
					))
			)
			.bodyToMono(TossBillingAuthResponse.class)
			.block();
	}

	/** 자동결제 승인(빌링키) */
	@Override
	public TossBillingChargeResponse chargeWithBillingKey(
		String billingKey,
		String customerKey,
		int amount,
		String orderId,
		String orderName,
		String customerEmail,
		String customerName,
		Integer taxFreeAmount,
		Integer taxExemptionAmount
	) {
		Map<String, Object> body = new HashMap<>();
		body.put("customerKey",    customerKey);
		body.put("amount",         amount);
		body.put("orderId",        orderId);
		body.put("orderName",      orderName);
		body.put("customerEmail",  customerEmail);
		body.put("customerName",   customerName);
		if (taxFreeAmount != null)     body.put("taxFreeAmount",      taxFreeAmount);
		if (taxExemptionAmount != null) body.put("taxExemptionAmount", taxExemptionAmount);

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
					.flatMap(b -> Mono.error(
						new TossException(ErrorCode.TOSS_API_ERROR, new RuntimeException(b))
					))
			)
			.bodyToMono(TossBillingChargeResponse.class)
			.block();
	}

	private static String encodeKey(String key) {
		return Base64.getEncoder()
			.encodeToString((key + ":").getBytes(StandardCharsets.UTF_8));
	}
}