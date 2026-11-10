package com.example.tableorder.service.payment;

import com.example.tableorder.dto.payment.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 결제 흐름의 진입점. 트랜잭션을 갖지 않고, 단계마다 {@link PaymentTxService}의
 * 트랜잭션 메서드를 호출한다. 외부(PG) 호출은 트랜잭션 사이에서 이 클래스가 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentTxService tx;

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
}
