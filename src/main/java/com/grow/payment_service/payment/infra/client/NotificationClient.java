package com.grow.payment_service.payment.infra.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.grow.payment_service.global.dto.RsData;

@FeignClient(
	name = "notification-service",
	url  = "${clients.notification.base-url:http://localhost:8082}",
	path = "/notifications"
)
public interface NotificationClient {

	@PostMapping
	RsData<Void> sendPaymentEvent(@RequestBody NotificationRequest req);
}