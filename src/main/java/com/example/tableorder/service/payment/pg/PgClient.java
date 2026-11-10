package com.example.tableorder.service.payment.pg;

import java.util.Optional;

/** 결제 대행사(PG) 연동. 구현은 {@link TossPaymentsClient}. */
public interface PgClient {

    /**
     * 결제 승인.
     *
     * @throws PgException.Declined      PG가 거절함 (승인 안 됨이 확실)
     * @throws PgException.ResultUnknown 결과를 알 수 없음 (재승인 금지, 조회로 확정)
     */
    PgPayment confirm(String paymentKey, String pgOrderId, int amount);

    /**
     * PG 주문번호로 결제 조회. PG에 기록이 없으면 빈 값.
     *
     * @throws PgException.ResultUnknown 조회 자체가 실패함
     */
    Optional<PgPayment> findByOrderId(String pgOrderId);

    /**
     * 전액 취소. 같은 결제의 취소는 항상 같은 멱등키로 보내, 재시도가 환불을 두 번 만들지 않게 한다.
     *
     * @throws PgException.Declined      PG가 취소를 거절함
     * @throws PgException.ResultUnknown 결과를 알 수 없음 (같은 멱등키로 다시 시도하면 안전)
     */
    PgPayment cancel(String paymentKey, String reason);
}
