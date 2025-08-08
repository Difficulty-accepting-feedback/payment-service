package com.grow.payment_service.payment.presentation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grow.payment_service.payment.application.service.impl.WebhookService;
import com.grow.payment_service.payment.presentation.dto.WebhookRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/webhooks/tosspayments")
@RequiredArgsConstructor
public class WebhookController {

	private final WebhookService webhookService;

	@PostMapping
	public ResponseEntity<Void> handle(@RequestBody WebhookRequest event) {
		log.info("[웹훅 수신] 이벤트 타입='{}', 데이터='{}'",
			event.getEventType(), event.getData());

		String type = event.getEventType();
		if ("PAYMENT_STATUS_CHANGED".equals(type)) {
			log.info("[웹훅 처리] 결제 상태 변경 이벤트 → orderId={}", event.getData().getOrderId());
			webhookService.onPaymentStatusChanged(event.getData());
		}
		else if ("CANCEL_STATUS_CHANGED".equals(type)) {
			log.info("[웹훅 처리] 취소 상태 변경 이벤트 → orderId={}", event.getData().getOrderId());
			webhookService.onCancelStatusChanged(event.getData());
		}
		else {
			log.warn("[웹훅 무시] 지원하지 않는 이벤트 타입='{}'", type);
		}

		// 반드시 200 OK 리턴
		return ResponseEntity.status(HttpStatus.OK).build();
	}
}