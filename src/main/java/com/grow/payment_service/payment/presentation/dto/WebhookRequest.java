package com.grow.payment_service.payment.presentation.dto;

import java.util.List;

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
		@JsonProperty("orderId")
		private String orderId;

		@JsonProperty("status")
		private String status;

		@JsonProperty("totalAmount")
		private Integer amount;

		@JsonProperty("currency")
		private String currency;

		@JsonProperty("method")
		private String method;

		@JsonProperty("requestedAt")
		private String requestedAt;

		@JsonProperty("approvedAt")
		private String approvedAt;

		@JsonProperty("easyPay")
		private EasyPay easyPay;

		@JsonProperty("cancels")
		private List<Cancel> cancels;

		@JsonProperty("customerEmail")
		private String customerEmail;

		@JsonProperty("customerName")
		private String customerName;

		@JsonProperty("receipt")
		private Receipt receipt;

		@Getter
		@NoArgsConstructor
		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class Receipt {
			@JsonProperty("url")
			private String url;
		}

		@Getter
		@NoArgsConstructor
		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class EasyPay {
			@JsonProperty("provider")
			private String provider;
			@JsonProperty("amount")
			private Integer amount;
		}

		@Getter
		@NoArgsConstructor
		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class Cancel {
			@JsonProperty("cancelReason")
			private String cancelReason;
			@JsonProperty("cancelAmount")
			private Integer cancelAmount;
			@JsonProperty("canceledAt")
			private String canceledAt;
		}
	}
}