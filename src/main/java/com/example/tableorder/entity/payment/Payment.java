package com.example.tableorder.entity.payment;

import com.example.tableorder.entity.order.Order;
import com.example.tableorder.exception.BadRequestException;
import com.example.tableorder.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 주문 1건의 결제. 주문당 행은 하나(order_id UNIQUE)이고, 상태 전이는 메서드로만 한다.
 * 금액은 항상 서버가 주문에서 계산한 값이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "pg_order_id", nullable = false, unique = true, length = 64)
    private String pgOrderId;

    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 50)
    private String method;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Payment ready(Order order, String pgOrderId, int amount) {
        if (amount <= 0) {
            throw new BadRequestException("결제할 금액이 없습니다.");
        }
        Payment p = new Payment();
        p.order = order;
        p.pgOrderId = pgOrderId;
        p.amount = amount;
        p.status = PaymentStatus.READY;
        return p;
    }

    /** FR-2: 클라이언트가 보낸 금액이 서버 금액과 같은지. 다르면 PG 호출 없이 400. */
    public void verifyAmount(Integer requested) {
        if (requested == null || !requested.equals(amount)) {
            throw new PaymentAmountMismatchException(amount, requested);
        }
    }

    /** 실패한 결제를 새 PG 주문번호로 다시 준비한다. */
    public void retry(String newPgOrderId) {
        transitionTo(PaymentStatus.READY);
        this.pgOrderId = newPgOrderId;
        this.paymentKey = null;
        this.method = null;
        this.approvedAt = null;
        this.failureCode = null;
        this.failureMessage = null;
    }

    /** PG 승인 확정 (TX2 또는 복구). 이미 DONE이면 아무것도 하지 않는다. */
    public void complete(String paymentKey, String method, LocalDateTime approvedAt) {
        if (status == PaymentStatus.DONE) {
            return;
        }
        transitionTo(PaymentStatus.DONE);
        this.paymentKey = paymentKey;
        this.method = method;
        this.approvedAt = approvedAt != null ? approvedAt : LocalDateTime.now();
        this.failureCode = null;
        this.failureMessage = null;
    }

    /** PG가 거절했거나, 승인되지 않은 채 끝났음이 확인됨. */
    public void fail(String code, String message) {
        if (status == PaymentStatus.FAILED) {
            return;
        }
        transitionTo(PaymentStatus.FAILED);
        this.failureCode = truncate(code, 100);
        this.failureMessage = truncate(message, 500);
    }

    /** 승인 결과를 모름 (타임아웃 등). 재승인하지 않고 조회로만 확정한다. */
    public void markUnknown() {
        if (status == PaymentStatus.UNKNOWN) {
            return;
        }
        transitionTo(PaymentStatus.UNKNOWN);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private void transitionTo(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessRuleException("결제 상태를 " + status + "에서 " + next + "(으)로 바꿀 수 없습니다.");
        }
        status = next;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
