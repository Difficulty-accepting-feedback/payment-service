package com.grow.payment_service.payment.domain.repository;

import java.util.List;
import java.util.Optional;

import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;

public interface PaymentHistoryRepository {
	PaymentHistory save(PaymentHistory paymentHistory);
	List<PaymentHistory> findByPaymentId(Long paymentId);

	// paymentId별로, 주어진 상태 목록에 해당하는 마지막 이력을 changedAt 기준 내림차순 정렬하여 한 건만 반환
	Optional<PaymentHistory> findLastByPaymentIdAndStatuses(
		Long paymentId,
		List<PayStatus> statuses
	);
}