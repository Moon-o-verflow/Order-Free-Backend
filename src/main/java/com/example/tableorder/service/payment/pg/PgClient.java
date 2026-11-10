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
}
