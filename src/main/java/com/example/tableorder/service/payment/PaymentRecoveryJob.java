package com.example.tableorder.service.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * FR-9 간이 대사 배치. 결과 미확정(IN_PROGRESS·UNKNOWN)으로 일정 시간 넘게 멈춘 결제를
 * 주기적으로 PG 조회로 확정한다. 기본: 1분마다, 1분 넘게 멈춘 결제 대상.
 * PG에 기록이 끝내 없는 결제는 5분 뒤 FAILED 로 정리된다(PaymentRecoveryService).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentRecoveryJob {

    private final PaymentRecoveryService recovery;

    @Value("${payment.recovery.stale-after:PT1M}")
    private Duration staleAfter;

    @Scheduled(fixedDelayString = "${payment.recovery.interval:PT1M}",
            initialDelayString = "${payment.recovery.interval:PT1M}")
    public void run() {
        int resolved = recovery.recoverStale(staleAfter);
        if (resolved > 0) {
            log.info("결제 복구 배치: {}건 확정", resolved);
        }
    }
}
