package com.example.tableorder.repository;

import com.example.tableorder.dto.order.LiveOrderDto;
import com.example.tableorder.entity.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o.table.store.id from Order o where o.id = :orderId")
    Optional<Long> findStoreIdById(@Param("orderId") Long orderId);

    @Query("""
        select new com.example.tableorder.dto.order.LiveOrderDto(
            o.id,
            t.id,
            o.status,
            o.createdAt,
            coalesce(sum(oi.unitPrice * oi.quantity), 0L)
        )
        from Order o
        join o.table t
        left join o.orderItems oi
        where t.store.id = :storeId
        group by o.id, t.id, o.status, o.createdAt
        order by o.createdAt desc
    """)
    List<LiveOrderDto> findLiveOrders(@Param("storeId") Long storeId);

    List<Order> findAllByTable_Store_IdAndTable_Id(Long storeId, Long tableId);

    @Query("""
        SELECT DISTINCT o FROM Order o
        LEFT JOIN FETCH o.orderItems oi
        LEFT JOIN FETCH oi.menuItem
        WHERE o.table.store.id = :storeId
        AND o.table.id = :tableId
        AND o.createdAt > :cutoffTime
        ORDER BY o.createdAt DESC
    """)
    List<Order> findAllByTable_Store_IdAndTable_IdAndCreatedAtAfter(
        @Param("storeId") Long storeId, 
        @Param("tableId") Long tableId, 
        @Param("cutoffTime") LocalDateTime cutoffTime
    );

}
