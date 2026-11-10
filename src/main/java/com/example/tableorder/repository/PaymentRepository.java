package com.example.tableorder.repository;

import com.example.tableorder.entity.payment.Payment;
import com.example.tableorder.entity.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder_Id(Long orderId);

    Optional<Payment> findByPgOrderId(String pgOrderId);

    /**
     * 승인 잠금: READY → IN_PROGRESS 조건부 전이.
     * MySQL은 같은 행의 UPDATE를 직렬화하므로, 동시 요청 중 하나만 1을 받는다.
     * 금액도 조건에 넣어 서버 금액과 다른 요청은 절대 잠금을 얻지 못한다.
     *
     * @return 1이면 이 요청이 PG 승인을 호출할 권리를 얻음, 0이면 다른 상태
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Payment p
           set p.status = com.example.tableorder.entity.payment.PaymentStatus.IN_PROGRESS,
               p.paymentKey = :paymentKey,
               p.updatedAt = :now,
               p.version = p.version + 1
         where p.pgOrderId = :pgOrderId
           and p.status = com.example.tableorder.entity.payment.PaymentStatus.READY
           and p.amount = :amount
    """)
    int acquireConfirmLock(@Param("pgOrderId") String pgOrderId,
                           @Param("paymentKey") String paymentKey,
                           @Param("amount") int amount,
                           @Param("now") LocalDateTime now);

    /**
     * 웹훅 이벤트 기록. 같은 키가 이미 있으면 0을 돌려준다(중복 수신).
     * 동시에 같은 키가 들어오면 MySQL이 키 단위로 직렬화하므로 하나만 1을 받는다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT IGNORE INTO payment_webhook_event (event_key, received_at) VALUES (:eventKey, :now)",
            nativeQuery = true)
    int recordWebhookEvent(@Param("eventKey") String eventKey, @Param("now") LocalDateTime now);

    @Query(value = "SELECT COUNT(*) FROM payment_webhook_event WHERE event_key = :eventKey", nativeQuery = true)
    long countWebhookEvent(@Param("eventKey") String eventKey);

    @Query("select p.order.table.store.id from Payment p where p.id = :paymentId")
    Optional<Long> findStoreIdById(@Param("paymentId") Long paymentId);

    /** 복구 대상: 결과 미확정 상태로 기준 시각보다 오래 멈춘 결제. */
    List<Payment> findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<PaymentStatus> statuses, LocalDateTime before);
}
