package com.example.tableorder.service.payment.pg;

import java.time.OffsetDateTime;

/**
 * PG가 돌려준 결제 정보 중 우리가 쓰는 필드.
 *
 * @param status 토스 결제 상태 (READY, IN_PROGRESS, WAITING_FOR_DEPOSIT, DONE, CANCELED,
 *               PARTIAL_CANCELED, ABORTED, EXPIRED)
 */
public record PgPayment(
        String paymentKey,
        String orderId,
        String status,
        String method,
        Integer totalAmount,
        OffsetDateTime approvedAt
) {
    public boolean isDone() {
        return "DONE".equals(status);
    }

    public boolean isCanceled() {
        return "CANCELED".equals(status);
    }

    /** 승인되지 않고 끝난 상태. */
    public boolean isFailed() {
        return "ABORTED".equals(status) || "EXPIRED".equals(status);
    }
}
