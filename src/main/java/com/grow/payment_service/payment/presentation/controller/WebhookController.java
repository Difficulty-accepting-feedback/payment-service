package com.grow.payment_service.payment.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.grow.payment_service.payment.application.service.impl.WebhookService;
import com.grow.payment_service.payment.presentation.dto.WebhookRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Hidden;

@Slf4j
@RestController
@RequestMapping("/api/v1/payment/webhooks/tosspayments")
@RequiredArgsConstructor
@Hidden
public class WebhookController {

	private final WebhookService webhookService;

	@PostMapping
	public ResponseEntity<Void> handle(@RequestBody WebhookRequest event) {
		String type   = event.getEventType();
		String status = event.getData().getStatus();
		String orderId= event.getData().getOrderId();

		log.info("[웹훅 수신] 이벤트 타입='{}', orderId='{}', status='{}'", type, orderId, status);

		if ("PAYMENT_STATUS_CHANGED".equals(type)) {
			log.info("[웹훅 처리] 결제 상태 변경 → orderId={}, status={}", orderId, status);

			if ("DONE".equals(status) || "FAILED".equals(status)) {
				webhookService.onPaymentStatusChanged(event.getData());
			} else if ("CANCELED".equals(status)) {
				webhookService.onCancelStatusChanged(event.getData());
			} else {
				log.warn("[웹훅 무시] 처리 대상이 아닌 status='{}'", status);
			}
		} else {
			log.warn("[웹훅 무시] 지원하지 않는 이벤트 타입='{}'", type);
		}
		return ResponseEntity.ok().build();
	}
}