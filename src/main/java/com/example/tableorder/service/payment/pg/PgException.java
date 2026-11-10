package com.example.tableorder.service.payment.pg;

/** PG 호출 실패. 결과가 확정됐는지에 따라 두 갈래로 나눈다. */
public sealed class PgException extends RuntimeException
        permits PgException.Declined, PgException.ResultUnknown {

    private final String code;

    protected PgException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** PG가 명확히 거절했다. 승인되지 않았음이 확실하다. */
    public static final class Declined extends PgException {
        public Declined(String code, String message) {
            super(code, message, null);
        }
    }

    /**
     * 승인됐는지 알 수 없다 (타임아웃, 네트워크 오류, PG 5xx, 이미 처리된 결제 등).
     * 이때는 절대 다시 승인하지 않고 PG 주문번호 조회로만 확정한다.
     */
    public static final class ResultUnknown extends PgException {
        public ResultUnknown(String code, String message, Throwable cause) {
            super(code, message, cause);
        }
    }
}
