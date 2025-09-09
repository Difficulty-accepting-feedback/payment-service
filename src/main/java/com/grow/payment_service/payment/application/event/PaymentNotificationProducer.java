package com.grow.payment_service.payment.application.event;

import java.time.LocalDateTime;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.grow.payment_service.global.util.JsonUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationProducer {

	private static final String TYPE = "PAYMENT";
	private static final String TOPIC = "payment.notification.requested";

	private final KafkaTemplate<String, String> kafkaTemplate;

	private void publish(
		Long memberId,
		String code,
		String title,
		String content,
		String orderId,
		Long amount
	) {
		PaymentNotificationEvent event = new PaymentNotificationEvent(
			memberId,
			code,
			TYPE,
			title,
			content,
			orderId,
			amount,
			LocalDateTime.now()
		);

		String key = String.valueOf(memberId);
		String payload = JsonUtils.toJsonString(event);

		kafkaTemplate.send(TOPIC, key, payload);
		log.info("[KAFKA][SENT] topic={}, key={}, type={}, code={}, orderId={}",
			TOPIC, key, TYPE, code, orderId);
	}

	/** 결제 승인 성공 */
	public void paymentApproved(Long memberId, String orderId, int amount) {
		publish(memberId, "APPROVED",
			"결제가 완료되었습니다",
			"주문 " + orderId + " 결제가 완료되었어요.",
			orderId, (long) amount);
	}

	/** 결제 실패 */
	public void paymentFailed(Long memberId, String orderId, int amount) {
		publish(memberId, "FAILED",
			"결제가 실패했습니다",
			"주문 " + orderId + " 결제가 실패했어요. 다시 시도해 주세요.",
			orderId, (long) amount);
	}

	/** 결제 취소 요청 접수 */
	public void cancelRequested(Long memberId, String orderId, int amount) {
		publish(memberId, "CANCEL_REQUESTED",
			"결제 취소 요청 접수",
			"주문 " + orderId + " 취소 요청을 접수했어요.",
			orderId, (long) amount);
	}

	/** 결제 취소 완료 */
	public void cancelled(Long memberId, String orderId, int amount) {
		publish(memberId, "CANCELLED",
			"결제가 취소되었습니다",
			"주문 " + orderId + " 결제가 취소되었어요.",
			orderId, (long) amount);
	}

	/** 자동결제 등록 완료(빌링키 발급) */
	public void billingKeyIssued(Long memberId, String orderId) {
		publish(memberId, "BILLING_KEY_ISSUED",
			"자동결제 등록 완료",
			"주문 " + orderId + " 자동결제가 등록되었어요.",
			orderId, null);
	}

	/** 자동결제 승인 완료 */
	public void autoBillingApproved(Long memberId, String orderId, int amount) {
		publish(memberId, "AUTO_BILLING_APPROVED",
			"자동결제 완료",
			"주문 " + orderId + " 자동결제가 완료되었어요.",
			orderId, (long) amount);
	}

	/** 자동결제 실패 */
	public void autoBillingFailed(Long memberId, String orderId, int amount) {
		publish(memberId, "AUTO_BILLING_FAILED",
			"자동결제 실패",
			"주문 " + orderId + " 자동결제가 실패했어요. 결제수단을 확인해 주세요.",
			orderId, (long) amount);
	}

	/** 구독 해지 예약(7일 초과 정책 등) */
	public void cancelScheduled(Long memberId, String orderId) {
		publish(memberId, "SUBSCRIPTION_CANCEL_SCHEDULED",
			"구독 해지 예약",
			"주문 " + orderId + "은 이번 달까지 이용되고 다음 달부터 청구되지 않아요.",
			orderId, null);
	}
}