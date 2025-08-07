package com.grow.payment_service.global.exception;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.grow.payment_service.global.exception.message.MessageService;
import com.grow.payment_service.exception.DomainException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

	private final MessageService messageService;

	/**
	 * 도메인 레이어 예외 처리
	 */
	@ExceptionHandler(DomainException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleDomainException(DomainException e, HttpServletRequest request) {
		log.warn("DomainException 발생 - URI: {} | message: {}", request.getRequestURI(), e.getMessage());
		return ErrorResponse.builder()
			.timestamp(LocalDateTime.now())
			.status(HttpStatus.BAD_REQUEST.value())
			.code("DOMAIN_ERROR")
			.message(e.getMessage())
			.path(request.getRequestURI())
			.build();
	}

	/**
	 * 비즈니스 서비스 예외 처리
	 */
	@ExceptionHandler(ServiceException.class)
	public ResponseEntity<ErrorResponse> handleServiceException(ServiceException e,
		HttpServletRequest request) {
		String message = messageService.getMessage(e.getErrorCode().getMessageCode());
		log.error("ServiceException 발생 - URI: {} | code: {} | message: {}",
			request.getRequestURI(),
			e.getErrorCode().getCode(),
			message);

		ErrorResponse errorResponse = ErrorResponse.of(e, request, messageService);
		return ResponseEntity.status(e.getErrorCode().getStatus()).body(errorResponse);
	}

	/**
	 * 데이터베이스 접근 오류 처리
	 */
	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<ErrorResponse> handleDataAccessException(DataAccessException e,
		HttpServletRequest request) {
		log.error("DB 접근 오류 발생 - URI: {} | message: {}", request.getRequestURI(), e.getMessage(), e);

		ErrorResponse errorResponse = ErrorResponse.builder()
			.timestamp(LocalDateTime.now())
			.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
			.code("500-1")
			.message("DB 접근 중 오류가 발생했습니다.")
			.path(request.getRequestURI())
			.build();

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
	}

	/**
	 * 그 외 예외 처리
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e,
		HttpServletRequest request) {
		log.error("예기치 않은 예외 발생 - URI: {} | message: {}", request.getRequestURI(), e.getMessage(), e);

		ErrorResponse errorResponse = ErrorResponse.builder()
			.timestamp(LocalDateTime.now())
			.status(HttpStatus.INTERNAL_SERVER_ERROR.value())
			.code("500-0")
			.message("알 수 없는 서버 오류가 발생했습니다.")
			.errors(List.of(e.getClass().getSimpleName() + ": " + e.getMessage()))
			.path(request.getRequestURI())
			.build();

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
	}
}