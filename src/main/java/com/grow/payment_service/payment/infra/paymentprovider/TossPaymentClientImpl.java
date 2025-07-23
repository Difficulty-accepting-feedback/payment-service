package com.grow.payment_service.payment.infra.paymentprovider;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
	public TossVirtualAccountResponse createVirtualAccount(
		String orderId,
		int amount,
		String orderName,
		String customerName,
		String bankCode,
		int validHours
	) {
		WebClient client = webClientBuilder
			.baseUrl(baseUrl)   // ${toss.base-url} 은 반드시 "https://api.tosspayments.com/v1" 으로만
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodeKey(secretKey))
			.build();

		return client.post()
			.uri("/virtual-accounts")
			.bodyValue(Map.of(
				"orderId",      orderId,
				"amount",       amount,
				"orderName",    orderName,
				"customerName", customerName,
				"bank",         bankCode,
				"validHours",   validHours
			))
			.retrieve()
			.onStatus(HttpStatusCode::isError, resp ->
				resp.bodyToMono(String.class)
					.flatMap(body -> Mono.error(new TossException("토스 가상계좌 발급 실패: " + body)))
			)
			.bodyToMono(TossVirtualAccountResponse.class)
			.block();
	}


	private static String encodeKey(String key) {
		return Base64.getEncoder()
			.encodeToString((key + ":").getBytes(StandardCharsets.UTF_8));
	}
}