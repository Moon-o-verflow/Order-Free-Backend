package com.example.tableorder.service.payment;

/** PG 호출 결과를 확인하지 못함. 같은 요청을 다시 보내도 안전하다(멱등). 503으로 응답한다. */
public class PgUnavailableException extends RuntimeException {

    public PgUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
