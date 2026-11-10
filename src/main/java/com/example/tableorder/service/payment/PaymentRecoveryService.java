package com.example.tableorder.service.payment;

import com.example.tableorder.dto.payment.PaymentResponse;
import com.example.tableorder.entity.payment.PaymentStatus;
import com.example.tableorder.service.payment.pg.PgClient;
import com.example.tableorder.service.payment.pg.PgException;
import com.example.tableorder.service.payment.pg.PgPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 결과가 확정되지 않은 결제(IN_PROGRESS·UNKNOWN)를 PG 주문번호 조회로 확정한다(FR-6, FR-9).
 * 어떤 경우에도 승인을 다시 호출하지 않는다 — 조회만 한다.
 *
 * <ul>
 *   <li>PG에 DONE 기록이 있음 → DONE</li>
 *   <li>PG에 ABORTED/EXPIRED → FAILED</li>
 *   <li>PG에 기록이 없음 → 기준 시간이 지났을 때만 FAILED(승인 요청이 PG에 닿지 않았음).
 *       그 전에는 아직 처리 중일 수 있으므로 그대로 둔다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final PaymentTxService tx;
    private final PgClient pgClient;

    @Value("${payment.recovery.give-up-after:PT5M}")
    private Duration giveUpAfter = Duration.ofMinutes(5);

    /** 결제 하나를 확정 시도한다. 확정하지 못하면 현재 상태를 그대로 돌려준다. */
    public PaymentResponse recover(Long paymentId) {
        PaymentTxService.PendingPayment p = tx.pending(paymentId);
        if (!p.status().isPending()) {
            return tx.get(paymentId);
        }

        Optional<PgPayment> pg;
        try {
            pg = pgClient.findByOrderId(p.pgOrderId());
        } catch (PgException.ResultUnknown e) {
            log.warn("결제 복구 조회 실패 — 다음에 다시 시도: paymentId={}, code={}", paymentId, e.code());
            return tx.get(paymentId);
        }

        if (pg.isPresent() && pg.get().isDone()) {
            log.info("결제 복구: PG 승인 확인 → DONE, paymentId={}", paymentId);
            return tx.markDone(paymentId, pg.get());
        }
        if (pg.isPresent() && pg.get().isFailed()) {
            return tx.markFailed(paymentId, "PG_" + pg.get().status(), "PG에서 승인되지 않고 종료된 결제");
        }
        if (pg.isEmpty() && p.updatedAt().isBefore(LocalDateTime.now().minus(giveUpAfter))) {
            return tx.markFailed(paymentId, "NOT_FOUND_AT_PG", "PG에 승인 기록이 없어 실패 처리");
        }
        return tx.get(paymentId); // 아직 판단할 수 없음
    }

    /** FR-9 배치에서 쓰는 진입점: 오래 멈춘 결제를 모두 확정 시도. */
    public int recoverStale(Duration olderThan) {
        int resolved = 0;
        for (Long id : tx.findStalePendingIds(LocalDateTime.now().minus(olderThan))) {
            try {
                PaymentStatus after = recover(id).status();
                if (!after.isPending()) {
                    resolved++;
                }
            } catch (RuntimeException e) {
                // 낙관적 락 충돌(동시에 TX2가 확정) 등 — 다음 주기에 다시 본다
                log.warn("결제 복구 실패: paymentId={}, {}", id, e.toString());
            }
        }
        return resolved;
    }
}
