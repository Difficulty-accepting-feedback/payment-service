package com.grow.payment_service.payment.infra.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "api-gateway", path = "/api/members")
public interface MemberClient {

	/**
	 * 본인 정보 조회
	 * @param memberId   X-Authorization-Id 헤더로 전달된 회원 ID
	 */
	@GetMapping("/me")
	MemberInfoResponse getMyInfo(
		@RequestHeader("X-Authorization-Id") Long memberId
	);
}