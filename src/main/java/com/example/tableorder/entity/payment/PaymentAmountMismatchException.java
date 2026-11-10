package com.example.tableorder.entity.payment;

import com.example.tableorder.exception.BadRequestException;

/** 승인 요청 금액이 서버에 저장된 결제 금액과 다를 때. */
public class PaymentAmountMismatchException extends BadRequestException {

    public PaymentAmountMismatchException(int expected, Integer actual) {
        super("결제 금액이 일치하지 않습니다. (요청 " + actual + "원, 결제 " + expected + "원)");
    }
}
