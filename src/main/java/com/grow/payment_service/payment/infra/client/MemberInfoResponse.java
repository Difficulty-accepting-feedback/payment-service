package com.grow.payment_service.payment.infra.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MemberInfoResponse {
	private Long memberId;
	private String email;
	private String nickname;
}