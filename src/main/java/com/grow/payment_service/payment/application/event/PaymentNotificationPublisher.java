package com.grow.payment_service.payment.application.event;

import java.time.LocalDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentNotificationPublisher {

	private static final String TYPE = "PAYMENT";
	private final ApplicationEventPublisher events;

	private void publish(
		Long memberId,
		String code,
		String title,
		String content,
		String orderId,
		Integer amount
	) {
		PaymentEvent event = new PaymentEvent(
			memberId,
			TYPE,
			code,
			title,
			content,
			orderId,
			amount,
			LocalDateTime.now()
		);
		events.publishEvent(event);
	}

	/** 결제 승인 성공 */
	public void paymentApproved(Long memberId, String orderId, int amount) {
		String title = "결제가 완료되었습니다";
		String content = "주문 " + orderId + " 결제가 완료되었어요.";
		publish(memberId, "APPROVED", title, content, orderId, Integer.valueOf(amount));
	}

	/** 결제 실패(필요 시 사용) */
	public void paymentFailed(Long memberId, String orderId, int amount) {
		String title = "결제가 실패했습니다";
		String content = "주문 " + orderId + " 결제가 실패했어요. 다시 시도해 주세요.";
		publish(memberId, "FAILED", title, content, orderId, Integer.valueOf(amount));
	}

	/** 결제 취소 요청 접수(필요 시 사용) */
	public void cancelRequested(Long memberId, String orderId, int amount) {
		String title = "결제 취소 요청 접수";
		String content = "주문 " + orderId + " 취소 요청을 접수했어요.";
		publish(memberId, "CANCEL_REQUESTED", title, content, orderId, Integer.valueOf(amount));
	}

	/** 결제 취소 완료 */
	public void cancelled(Long memberId, String orderId, int amount) {
		String title = "결제가 취소되었습니다";
		String content = "주문 " + orderId + " 결제가 취소되었어요.";
		publish(memberId, "CANCELLED", title, content, orderId, Integer.valueOf(amount));
	}

	/** 자동결제 등록 완료(빌링키 발급) */
	public void billingKeyIssued(Long memberId, String orderId) {
		String title = "자동결제 등록 완료";
		String content = "주문 " + orderId + " 자동결제가 등록되었어요.";
		publish(memberId, "BILLING_KEY_ISSUED", title, content, orderId, null);
	}

	/** 자동결제 승인 완료 */
	public void autoBillingApproved(Long memberId, String orderId, int amount) {
		String title = "자동결제 완료";
		String content = "주문 " + orderId + " 자동결제가 완료되었어요.";
		publish(memberId, "AUTO_BILLING_APPROVED", title, content, orderId, Integer.valueOf(amount));
	}

	/** 자동결제 실패 */
	public void autoBillingFailed(Long memberId, String orderId, int amount) {
		String title = "자동결제 실패";
		String content = "주문 " + orderId + " 자동결제가 실패했어요. 결제수단을 확인해 주세요.";
		publish(memberId, "AUTO_BILLING_FAILED", title, content, orderId, Integer.valueOf(amount));
	}

	/** 구독 해지 예약(7일 초과 정책 등) */
	public void cancelScheduled(Long memberId, String orderId) {
		String title = "구독 해지 예약";
		String content = "주문 " + orderId + "은 이번 달까지 이용되고 다음 달부터 청구되지 않아요.";
		publish(memberId, "SUBSCRIPTION_CANCEL_SCHEDULED", title, content, orderId, null);
	}
}