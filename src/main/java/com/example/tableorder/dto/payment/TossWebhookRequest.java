package com.example.tableorder.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스페이먼츠 웹훅 (PAYMENT_STATUS_CHANGED). 필요한 필드만 받는다.
 * 본문은 위조될 수 있으므로 중복 판별 키로만 쓰고, 실제 상태는 PG 조회로 확인한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossWebhookRequest(String eventType, String createdAt, Data data) {

    public static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String paymentKey, String orderId, String status) {
    }

    /** 같은 이벤트의 재전송을 가려내는 키: 결제 키 + 상태 + 이벤트 시각. */
    public String eventKey() {
        String key = data.paymentKey() + ":" + data.status() + ":" + createdAt;
        return key.length() <= 200 ? key : key.substring(0, 200);
    }

    public boolean isPaymentStatusChange() {
        return PAYMENT_STATUS_CHANGED.equals(eventType) && data != null && data.orderId() != null;
    }
}
