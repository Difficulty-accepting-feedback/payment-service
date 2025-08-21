package com.grow.payment_service.payment.infra.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.grow.payment_service.global.dto.RsData;

// TODO(GATEWAY): 게이트웨이 도입 시 /api/members/me (사용자 토큰 기반)로 교체 또는 제거.

@FeignClient(
	name = "member-service",
	url  = "${clients.member.base-url:http://localhost:8080}",
	path = "/internal/members"
)
public interface MemberClient {

	/** 로컬/개발 임시: memberId로 이메일/닉네임 조회 */
	@GetMapping("/{memberId}")
	RsData<MemberInfoResponse> getMyInfo(@PathVariable("memberId") Long memberId);
}