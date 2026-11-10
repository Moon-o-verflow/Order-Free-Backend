package com.example.tableorder.entity.order;

import com.example.tableorder.entity.store.StoreTable;
import com.example.tableorder.exception.BusinessRuleException;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "`order`")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private StoreTable table;

    /** 상태 변경은 {@link #changeStatus(OrderStatus)} 로만 한다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter(AccessLevel.NONE)
    @Builder.Default
    private OrderStatus status = OrderStatus.ORDERED;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    // 계산된 총액을 가져오는 메서드 (DB 필드와 별도로 계산)
    public Integer getCalculatedTotalPrice() {
        return orderItems.stream()
                .mapToInt(item -> item.getUnitPrice() * item.getQuantity())
                .sum();
    }

    /**
     * 허용된 전이만 수행한다. 같은 상태로의 변경은 아무 일도 하지 않는다.
     *
     * @throws BusinessRuleException 허용되지 않은 전이일 때
     */
    public void changeStatus(OrderStatus next) {
        if (next == null) {
            throw new BusinessRuleException("변경할 주문 상태가 없습니다.");
        }
        if (status == next) {
            return;
        }
        if (!status.canTransitionTo(next)) {
            throw new BusinessRuleException(
                    "주문 상태를 " + status + "에서 " + next + "(으)로 바꿀 수 없습니다. 가능한 상태: " + status.nextStatuses());
        }
        status = next;
    }

    // 주문 항목 추가 메서드
    public void addOrderItem(OrderItem orderItem) {
        orderItems.add(orderItem);
        orderItem.setOrder(this);
    }
}
