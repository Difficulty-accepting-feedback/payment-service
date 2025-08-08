package com.grow.payment_service.payment.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookRequest {

	/** JSON의 "eventType" 필드와 매핑 */
	@JsonProperty("eventType")
	private String eventType;

	@JsonProperty("createdAt")
	private String createdAt;

	/** JSON의 "data" 오브젝트와 매핑 */
	@JsonProperty("data")
	private WebhookData data;

	@Getter
	@NoArgsConstructor
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class WebhookData {
		private String orderId;
		private String status;
		private Integer amount;
		private String customerEmail;
		private String customerName;
	}
}