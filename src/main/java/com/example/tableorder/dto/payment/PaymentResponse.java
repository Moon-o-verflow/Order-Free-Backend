package com.example.tableorder.dto.payment;

import com.example.tableorder.entity.payment.Payment;
import com.example.tableorder.entity.payment.PaymentStatus;

import java.time.LocalDateTime;

/**
 * 결제 정보. 결제창은 pgOrderId·amount·orderName 으로 토스 결제를 요청한다.
 */
public record PaymentResponse(
        Long paymentId,
        Long orderId,
        String pgOrderId,
        String orderName,
        Integer amount,
        PaymentStatus status,
        String method,
        LocalDateTime approvedAt
) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(
                p.getId(), p.getOrder().getId(), p.getPgOrderId(), "주문 #" + p.getOrder().getId(),
                p.getAmount(), p.getStatus(), p.getMethod(), p.getApprovedAt());
    }
}
