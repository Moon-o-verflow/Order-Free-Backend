package com.example.tableorder.service.payment;

import com.example.tableorder.exception.BadRequestException;

/** PG가 승인을 거절함. 결제는 FAILED 로 기록됐고, 다시 준비하면 새 주문번호로 재시도할 수 있다. */
public class PaymentFailedException extends BadRequestException {

    public PaymentFailedException(String code, String message) {
        super("결제가 거절되었습니다 [" + code + "] " + (message != null ? message : ""));
    }
}
