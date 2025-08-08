package com.grow.payment_service.payment.infra.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.grow.payment_service.global.dto.RsData;

@FeignClient(
	name = "member-service",
	url = "localhost:8080",
	path = "/api/members"
)
public interface MemberClient {

	/**
	 * 본인 정보 조회
	 * @param memberId   X-Authorization-Id 헤더로 전달된 회원 ID
	 */
	@GetMapping("/me")
	RsData<MemberInfoResponse> getMyInfo(
		@RequestHeader("X-Authorization-Id") Long memberId
	);
}