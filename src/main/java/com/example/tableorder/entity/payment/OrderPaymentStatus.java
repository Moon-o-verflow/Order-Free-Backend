package com.example.tableorder.entity.payment;

/**
 * 주문 입장에서 본 결제 상태. 주문 상태(조리·서빙 흐름)와 분리해서, 결제 행에서 계산한다.
 */
public enum OrderPaymentStatus {
    UNPAID,
    PAID,
    REFUNDED;

    public static OrderPaymentStatus of(PaymentStatus status) {
        if (status == null) {
            return UNPAID;
        }
        return switch (status) {
            case DONE -> PAID;
            case CANCELED -> REFUNDED;
            default -> UNPAID;
        };
    }
}
