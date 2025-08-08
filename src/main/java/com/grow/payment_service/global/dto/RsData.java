package com.grow.payment_service.global.dto;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.ToString;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RsData<T> {

	@NonNull
	private String code;
	@NonNull
	private String msg;
	private T data;

	// Jackson 이 JSON 필드를 맵핑하도록 @JsonCreator
	@JsonCreator
	public RsData(
		@JsonProperty("code") String code,
		@JsonProperty("msg") String msg,
		@JsonProperty("data") T data
	) {
		this.code = code;
		this.msg  = msg;
		this.data = data;
	}

	public RsData(String code, String msg) {
		this(code, msg, null);
	}

	@JsonIgnore
	public int getStatusCode() {
		String statusCodeStr = code.split("-")[0];
		return Integer.parseInt(statusCodeStr);
	}
}