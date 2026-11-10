package com.example.tableorder.service.payment;

import com.example.tableorder.dto.payment.PaymentConfirmRequest;
import com.example.tableorder.dto.payment.PaymentResponse;
import com.example.tableorder.service.payment.pg.PgClient;
import com.example.tableorder.service.payment.pg.PgException;
import com.example.tableorder.service.payment.pg.PgPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 결제 흐름의 진입점. 트랜잭션을 갖지 않고, 단계마다 {@link PaymentTxService}의
 * 트랜잭션 메서드를 호출한다. 외부(PG) 호출은 트랜잭션 사이에서 이 클래스가 한다.
 *
 * <pre>
 * TX1  금액 검증 + READY→IN_PROGRESS (커밋 = 잠금)
 * ───  PG 승인 호출 (트랜잭션 밖, 커넥션·락을 잡지 않음)
 * TX2  IN_PROGRESS→DONE / FAILED / UNKNOWN
 * </pre>
 * PG가 승인한 뒤 TX2가 실패해도 결제는 IN_PROGRESS 로 남아 있어 복구 배치가 조회로 DONE 을 확정한다.
 * 하나의 트랜잭션 안에서 PG를 불렀다면 롤백으로 "PG 승인됨 + DB 미결제"가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentTxService tx;
    private final PgClient pgClient;
    private final PaymentRecoveryService recovery;

    /**
     * FR-1. 같은 주문에 대한 첫 준비 요청이 동시에 와도 결제 행은 하나만 생긴다.
     * 진 쪽은 order_id UNIQUE 위반으로 롤백되고, 새 트랜잭션에서 이긴 쪽의 결제를 읽어 돌려준다.
     */
    public PaymentTxService.PrepareResult prepare(Long storeId, Long tableId, Long orderId) {
        try {
            return tx.prepare(storeId, tableId, orderId);
        } catch (DataIntegrityViolationException e) {
            log.info("결제 준비 경합 — 기존 결제 반환: orderId={}", orderId);
            PaymentResponse existing = tx.findByOrder(orderId);
            return new PaymentTxService.PrepareResult(existing, false);
        }
    }

    /**
     * FR-3~6 결제 승인.
     *
     * @return DONE, 또는 결과 미확정이면 UNKNOWN/IN_PROGRESS (클라이언트는 조회로 확인)
     * @throws PaymentFailedException PG가 거절함 (결제는 FAILED 로 기록됨)
     */
    public PaymentResponse confirm(PaymentConfirmRequest req) {
        PaymentTxService.ConfirmTicket ticket = tx.markInProgress(req);          // TX1
        if (!ticket.shouldCallPg()) {
            return ticket.alreadyDone();                                          // FR-5: PG 재호출 없음
        }

        PgPayment approved;
        try {
            approved = pgClient.confirm(req.paymentKey(), req.orderId(), req.amount()); // 트랜잭션 밖
        } catch (PgException.Declined e) {
            tx.markFailed(ticket.paymentId(), e.code(), e.getMessage());          // TX2'
            throw new PaymentFailedException(e.code(), e.getMessage());
        } catch (PgException.ResultUnknown e) {
            log.warn("PG 승인 결과 미확정 — 조회로 확정: paymentId={}, code={}", ticket.paymentId(), e.code());
            tx.markUnknown(ticket.paymentId());                                   // TX2''
            return recovery.recover(ticket.paymentId());                          // 재승인 없이 조회만
        }

        try {
            return tx.markDone(ticket.paymentId(), approved);                     // TX2
        } catch (RuntimeException e) {
            // PG는 승인했는데 기록에 실패: 결제는 IN_PROGRESS 로 남아 있고 복구 배치가 확정한다
            log.error("PG 승인 후 DB 기록 실패 — 복구 대기: paymentId={}", ticket.paymentId(), e);
            return tx.get(ticket.paymentId());
        }
    }
}
