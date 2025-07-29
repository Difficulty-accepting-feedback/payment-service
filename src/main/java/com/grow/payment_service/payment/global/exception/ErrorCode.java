package com.grow.payment_service.payment.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {;

	private final HttpStatus status;
	private final String code;
	private final String messageCode;
}