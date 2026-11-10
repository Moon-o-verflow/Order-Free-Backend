package com.example.tableorder.service.payment;

import com.example.tableorder.dto.payment.PaymentResponse;
import com.example.tableorder.entity.order.Order;
import com.example.tableorder.entity.order.OrderStatus;
import com.example.tableorder.entity.payment.Payment;
import com.example.tableorder.entity.payment.PaymentStatus;
import com.example.tableorder.exception.ConflictException;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.repository.OrderRepository;
import com.example.tableorder.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * FR-1 결제 준비. 주문에 결제가 없으면 READY 로 만들고, 있으면 기존 결제를 돌려준다.
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

    @Transactional(readOnly = true)
    public PaymentStatus statusOfOrder(Long orderId) {
        return paymentRepository.findByOrder_Id(orderId).map(Payment::getStatus).orElse(null);
    }
}
