package com.example.tableorder.entity.payment;

import java.util.EnumSet;
import java.util.Set;

/**
 * 결제 상태와 허용 전이.
 *
 * <pre>
 * READY ──▶ IN_PROGRESS ──▶ DONE ──▶ CANCELED
 *   ▲            │  │
 *   │            │  └──▶ UNKNOWN ──▶ DONE / FAILED   (타임아웃: PG 조회로만 확정)
 *   └── FAILED ◀─┘
 * </pre>
 *
 * IN_PROGRESS 는 "승인 처리 중" 잠금 역할을 한다. READY → IN_PROGRESS 조건부 전이에
 * 성공한 요청 하나만 PG 승인을 호출한다.
 */
public enum PaymentStatus {
    READY,
    IN_PROGRESS,
    DONE,
    FAILED,
    UNKNOWN,
    CANCELED;

    public Set<PaymentStatus> nextStatuses() {
        return switch (this) {
            case READY -> EnumSet.of(IN_PROGRESS);
            case IN_PROGRESS -> EnumSet.of(DONE, FAILED, UNKNOWN);
            case UNKNOWN -> EnumSet.of(DONE, FAILED);
            case DONE -> EnumSet.of(CANCELED);
            case FAILED -> EnumSet.of(READY); // 새 PG 주문번호로 재시도
            case CANCELED -> EnumSet.noneOf(PaymentStatus.class);
        };
    }

    public boolean canTransitionTo(PaymentStatus next) {
        return nextStatuses().contains(next);
    }

    /** PG 결과가 아직 확정되지 않은 상태 (복구 배치 대상). */
    public boolean isPending() {
        return this == IN_PROGRESS || this == UNKNOWN;
    }
}
