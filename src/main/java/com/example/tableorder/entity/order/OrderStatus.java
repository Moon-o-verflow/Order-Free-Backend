package com.example.tableorder.entity.order;

import com.example.tableorder.exception.BadRequestException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 주문 상태와 허용 전이.
 *
 * <pre>
 * ORDERED ──▶ PREPARING ──▶ SERVED ──▶ COMPLETED
 *    │            │
 *    └────────────┴──▶ CANCELLED
 * </pre>
 */
public enum OrderStatus {

    ORDERED("주문접수"),
    PREPARING("조리중"),
    SERVED("서빙완료"),
    COMPLETED("완료"),
    CANCELLED("취소");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 이 상태에서 넘어갈 수 있는 다음 상태들. */
    public Set<OrderStatus> nextStatuses() {
        return switch (this) {
            case ORDERED -> EnumSet.of(PREPARING, CANCELLED);
            case PREPARING -> EnumSet.of(SERVED, CANCELLED);
            case SERVED -> EnumSet.of(COMPLETED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return nextStatuses().contains(next);
    }

    /** 대기열·매출 집계에서 빠지는 종료 상태인지. */
    public boolean isFinished() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** 요청 값(대소문자 무관)을 상태로 바꾼다. 모르는 값이면 400. */
    public static OrderStatus from(String value) {
        if (value != null) {
            String key = value.trim().toUpperCase(Locale.ROOT);
            for (OrderStatus s : values()) {
                if (s.name().equals(key)) {
                    return s;
                }
            }
        }
        throw new BadRequestException("알 수 없는 주문 상태: " + value + " (허용: "
                + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", ")) + ")");
    }
}
