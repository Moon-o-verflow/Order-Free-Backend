package com.example.tableorder.repository;

import com.example.tableorder.dto.sales.SalesByDateDto;
import com.example.tableorder.dto.sales.SalesByMenuDto;
import com.example.tableorder.dto.sales.SalesByTableDto;
import com.example.tableorder.entity.order.Order;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SalesRepository extends JpaRepository<Order, Long> {

    // 4) 기간별 매출 요약 (일 단위)
    @Query("""
        select new com.example.tableorder.dto.sales.SalesByDateDto(
            cast(function('date', o.createdAt) as java.time.LocalDate),
            coalesce(sum(oi.unitPrice * oi.quantity), 0),
            count(distinct o.id)
        )
        from Order o
        join o.table t
        left join o.orderItems oi
        where t.store.id = :storeId
          and o.createdAt between :start and :end
          and o.status <> com.example.tableorder.entity.order.OrderStatus.CANCELLED
        group by function('date', o.createdAt)
        order by function('date', o.createdAt) asc
    """)
    List<SalesByDateDto> sumByDate(
            @Param("storeId") Long storeId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // 5) 메뉴별 매출 집계
    @Query("""
        select new com.example.tableorder.dto.sales.SalesByMenuDto(
            mi.id,
            coalesce(oi.menuName, cast(mi.id as string)),
            coalesce(sum(oi.quantity), 0),
            coalesce(sum(oi.unitPrice * oi.quantity), 0)
        )
        from Order o
        join o.table t
        join o.orderItems oi
        left join oi.menuItem mi
        where t.store.id = :storeId
          and o.createdAt between :start and :end
          and o.status <> com.example.tableorder.entity.order.OrderStatus.CANCELLED
        group by mi.id, oi.menuName
        order by coalesce(sum(oi.unitPrice * oi.quantity), 0) desc
    """)
    List<SalesByMenuDto> sumByMenu(
            @Param("storeId") Long storeId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // 6) 특정 일자 테이블별 매출
    @Query("""
        select new com.example.tableorder.dto.sales.SalesByTableDto(
            t.id,
            coalesce(sum(oi.unitPrice * oi.quantity), 0)
        )
        from Order o
        join o.table t
        left join o.orderItems oi
        where t.store.id = :storeId
          and o.createdAt between :start and :end
          and o.status <> com.example.tableorder.entity.order.OrderStatus.CANCELLED
        group by t.id
        order by t.id asc
    """)
    List<SalesByTableDto> sumByTableForDate(
            @Param("storeId") Long storeId,
            @Param("start") LocalDateTime startInclusive,
            @Param("end") LocalDateTime endExclusive
    );
}
