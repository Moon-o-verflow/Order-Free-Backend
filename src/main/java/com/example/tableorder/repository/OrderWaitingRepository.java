package com.example.tableorder.repository;

import com.example.tableorder.entity.order.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderWaitingRepository extends JpaRepository<Order, Long> {

    // 스토어의 "활성 주문" (완료/취소 제외) 대기열 조회
    @Query("""
        select distinct o
        from Order o
        join o.table t
        left join fetch o.orderItems oi
        left join fetch oi.menuItem mi
        where t.store.id = :storeId
          and o.status not in (com.example.tableorder.entity.order.OrderStatus.COMPLETED,
                               com.example.tableorder.entity.order.OrderStatus.CANCELLED)
        order by o.createdAt asc, o.id asc
    """)
    List<Order> findActiveOrdersForStore(@Param("storeId") Long storeId);

    // 단일 주문 + 아이템 fetch
    @Query("""
        select o
        from Order o
        left join fetch o.table t
        left join fetch o.orderItems oi
        left join fetch oi.menuItem mi
        where o.id = :orderId
    """)
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);
}
