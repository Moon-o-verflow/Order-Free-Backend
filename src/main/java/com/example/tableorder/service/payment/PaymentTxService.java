package com.example.tableorder.service.payment;

import com.example.tableorder.dto.payment.PaymentConfirmRequest;
import com.example.tableorder.dto.payment.PaymentResponse;
import com.example.tableorder.entity.order.Order;
import com.example.tableorder.entity.order.OrderStatus;
import com.example.tableorder.entity.payment.Payment;
import com.example.tableorder.entity.payment.PaymentStatus;
import com.example.tableorder.exception.ConflictException;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.repository.OrderRepository;
import com.example.tableorder.repository.PaymentRepository;
import com.example.tableorder.service.payment.pg.PgPayment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

/**
 * 결제의 DB 작업 단위. 메서드 하나가 트랜잭션 하나다.
 * PG 호출 같은 외부 I/O는 여기서 하지 않는다 — {@link PaymentFacade}가 트랜잭션 사이에서 한다.
 * (같은 클래스 안의 @Transactional 호출은 프록시를 거치지 않으므로 Facade와 분리했다.)
 */
@Service
@RequiredArgsConstructor
public class PaymentTxService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PgOrderIdGenerator pgOrderIdGenerator;

    public record PrepareResult(PaymentResponse payment, boolean created) {
    }

    /**
     * TX1의 결과. {@code alreadyDone}이 있으면 이미 승인된 결제에 대한 같은 요청이라 PG를 부르지 않는다.
     */
    public record ConfirmTicket(Long paymentId, PaymentResponse alreadyDone) {
        public boolean shouldCallPg() {
            return alreadyDone == null;
        }
    }

    /** 복구 대상 결제 요약. */
    public record PendingPayment(Long paymentId, String pgOrderId, PaymentStatus status, LocalDateTime updatedAt) {
    }

    // ------------------------------------------------------------------ 준비 (FR-1)

    /**
     * 주문에 결제가 없으면 READY 로 만들고, 있으면 기존 결제를 돌려준다.
     * 동시에 처음 요청이 여러 개 오면 order_id UNIQUE 위반이 나는데, 이는 Facade가 처리한다.
     */
    @Transactional
    public PrepareResult prepare(Long storeId, Long tableId, Long orderId) {
        Order order = orderRepository.findByIdAndTable_IdAndTable_Store_Id(orderId, tableId, storeId)
                .orElseThrow(() -> new NotFoundException("주문을 찾을 수 없습니다: " + orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ConflictException("취소된 주문은 결제할 수 없습니다.");
        }

        Payment existing = paymentRepository.findByOrder_Id(orderId).orElse(null);
        if (existing == null) {
            Payment created = paymentRepository.saveAndFlush(
                    Payment.ready(order, pgOrderIdGenerator.next(orderId), order.getTotalPrice()));
            return new PrepareResult(PaymentResponse.from(created), true);
        }

        switch (existing.getStatus()) {
            case FAILED -> existing.retry(pgOrderIdGenerator.next(orderId));
            case CANCELED -> throw new ConflictException("이미 취소된 결제입니다.");
            default -> { } // READY·처리 중·완료: 기존 결제를 그대로 돌려준다
        }
        return new PrepareResult(PaymentResponse.from(existing), false);
    }

    // ------------------------------------------------------------------ 승인 (FR-2, 3, 5)

    /**
     * TX1. 금액을 검증하고 READY → IN_PROGRESS 조건부 전이로 "승인 권리"를 얻는다.
     * 커밋된 IN_PROGRESS 가 다른 요청에게 보이는 잠금 역할을 한다.
     *
     * @throws com.example.tableorder.entity.payment.PaymentAmountMismatchException 금액 불일치 (400, PG 호출 없음)
     * @throws ConflictException 다른 요청이 처리 중이거나 승인할 수 없는 상태 (409)
     */
    @Transactional
    public ConfirmTicket markInProgress(PaymentConfirmRequest req) {
        Payment payment = paymentRepository.findByPgOrderId(req.orderId())
                .orElseThrow(() -> new NotFoundException("결제를 찾을 수 없습니다: " + req.orderId()));
        payment.verifyAmount(req.amount());

        int acquired = paymentRepository.acquireConfirmLock(
                req.orderId(), req.paymentKey(), req.amount(), LocalDateTime.now());
        if (acquired == 1) {
            return new ConfirmTicket(payment.getId(), null);
        }

        // 잠금을 못 얻었다: 현재 상태를 다시 읽어 판단 (clearAutomatically 로 1차 캐시는 비워짐)
        Payment current = paymentRepository.findById(payment.getId()).orElseThrow();
        if (current.getStatus() == PaymentStatus.DONE && req.paymentKey().equals(current.getPaymentKey())) {
            return new ConfirmTicket(current.getId(), PaymentResponse.from(current)); // FR-5 멱등 재요청
        }
        throw switch (current.getStatus()) {
            case IN_PROGRESS, UNKNOWN -> new ConflictException("결제를 처리하고 있습니다. 잠시 후 결과를 조회하세요.");
            case DONE -> new ConflictException("이미 다른 결제 키로 승인된 결제입니다.");
            default -> new ConflictException("승인할 수 없는 결제 상태입니다: " + current.getStatus());
        };
    }

    /** TX2. PG 승인 성공을 기록한다. */
    @Transactional
    public PaymentResponse markDone(Long paymentId, PgPayment pg) {
        Payment payment = load(paymentId);
        payment.complete(pg.paymentKey(), pg.method(),
                pg.approvedAt() != null ? pg.approvedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() : null);
        return PaymentResponse.from(payment);
    }

    /** TX2'. PG가 거절함. */
    @Transactional
    public PaymentResponse markFailed(Long paymentId, String code, String message) {
        Payment payment = load(paymentId);
        payment.fail(code, message);
        return PaymentResponse.from(payment);
    }

    /** TX2''. 결과 미확정. */
    @Transactional
    public PaymentResponse markUnknown(Long paymentId) {
        Payment payment = load(paymentId);
        payment.markUnknown();
        return PaymentResponse.from(payment);
    }

    // ------------------------------------------------------------------ 취소 (FR-8)

    /** 취소 대상 확인. 이미 취소됐으면 그 결과를, 아니면 PG 취소에 쓸 결제 키를 돌려준다. */
    public record CancelTicket(String paymentKey, PaymentResponse alreadyCanceled) {
    }

    @Transactional(readOnly = true)
    public CancelTicket cancelTicket(Long paymentId) {
        Payment payment = load(paymentId);
        return switch (payment.getStatus()) {
            case CANCELED -> new CancelTicket(null, PaymentResponse.from(payment));
            case DONE -> new CancelTicket(payment.getPaymentKey(), null);
            default -> throw new ConflictException("승인 완료(DONE)된 결제만 취소할 수 있습니다. 현재: " + payment.getStatus());
        };
    }

    @Transactional
    public PaymentResponse markCanceled(Long paymentId) {
        Payment payment = load(paymentId);
        payment.cancel();
        return PaymentResponse.from(payment);
    }

    // ------------------------------------------------------------------ 웹훅 (FR-7)

    public enum WebhookOutcome { UPDATED, NO_CHANGE, DUPLICATE, UNKNOWN_PAYMENT }

    @Transactional(readOnly = true)
    public boolean webhookAlreadyProcessed(String eventKey) {
        return paymentRepository.countWebhookEvent(eventKey) > 0;
    }

    /**
     * PG에서 확인한 실제 상태를 반영한다. 이벤트 기록과 상태 변경을 한 트랜잭션에서 하므로,
     * 반영이 실패하면 이벤트 기록도 롤백되어 PG의 재전송 때 다시 처리된다.
     */
    @Transactional
    public WebhookOutcome applyPgState(String eventKey, PgPayment pg) {
        if (paymentRepository.recordWebhookEvent(eventKey, LocalDateTime.now()) == 0) {
            return WebhookOutcome.DUPLICATE;
        }
        Payment payment = paymentRepository.findByPgOrderId(pg.orderId()).orElse(null);
        if (payment == null) {
            return WebhookOutcome.UNKNOWN_PAYMENT;
        }
        PaymentStatus before = payment.getStatus();
        if (pg.isDone() && before.isPending()) {
            payment.complete(pg.paymentKey(), pg.method(),
                    pg.approvedAt() != null ? pg.approvedAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() : null);
        } else if (pg.isCanceled() && before == PaymentStatus.DONE) {
            payment.cancel(); // 토스 상점관리자에서 직접 취소한 경우 등
        } else if (pg.isFailed() && before.isPending()) {
            payment.fail("PG_" + pg.status(), "PG에서 승인되지 않고 종료된 결제");
        }
        return payment.getStatus() == before ? WebhookOutcome.NO_CHANGE : WebhookOutcome.UPDATED;
    }

    // ------------------------------------------------------------------ 조회·복구

    @Transactional(readOnly = true)
    public PaymentResponse get(Long paymentId) {
        return PaymentResponse.from(load(paymentId));
    }

    @Transactional(readOnly = true)
    public PendingPayment pending(Long paymentId) {
        Payment p = load(paymentId);
        return new PendingPayment(p.getId(), p.getPgOrderId(), p.getStatus(), p.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<Long> findStalePendingIds(LocalDateTime before) {
        return paymentRepository.findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        EnumSet.of(PaymentStatus.IN_PROGRESS, PaymentStatus.UNKNOWN), before)
                .stream().map(Payment::getId).toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse findByOrder(Long orderId) {
        return paymentRepository.findByOrder_Id(orderId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new NotFoundException("결제를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PaymentResponse findByPgOrderId(String pgOrderId) {
        return paymentRepository.findByPgOrderId(pgOrderId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new NotFoundException("결제를 찾을 수 없습니다."));
    }

    private Payment load(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("결제를 찾을 수 없습니다: " + paymentId));
    }
}
