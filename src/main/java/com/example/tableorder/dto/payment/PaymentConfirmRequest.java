package com.example.tableorder.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 결제창 successUrl 로 받은 값을 그대로 보낸다.
 *
 * @param paymentKey 토스가 발급한 결제 키
 * @param orderId    PG 주문번호(pgOrderId)
 * @param amount     결제창에 넘긴 금액. 서버 금액과 다르면 400
 */
public record PaymentConfirmRequest(
        @NotBlank @Size(max = 200) String paymentKey,
        @NotBlank @Size(min = 6, max = 64) String orderId,
        @NotNull Integer amount
) {
}
