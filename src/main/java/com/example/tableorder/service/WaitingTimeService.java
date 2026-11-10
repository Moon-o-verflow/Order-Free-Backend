package com.example.tableorder.service;

import com.example.tableorder.entity.order.OrderStatus;

import com.example.tableorder.dto.waiting.OrderWaitingTimeDto;
import com.example.tableorder.dto.waiting.SingleWaitingTimeDto;
import com.example.tableorder.entity.order.Order;
import com.example.tableorder.entity.order.OrderItem;
import com.example.tableorder.entity.menu.MenuItem;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.repository.OrderWaitingRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WaitingTimeService {

    private final OrderWaitingRepository orderWaitingRepository;

    // 상태 보정 계수: 남은 작업 비율
    private static double statusFactor(OrderStatus status) {
        return switch (status) {
            case ORDERED -> 1.0;
            case PREPARING -> 0.6;
            case SERVED, COMPLETED, CANCELLED -> 0.0;
        };
    }

    // 동시 작업(바리스타/주방 병렬) 고려
    private static final double CONCURRENCY_COEFF = 0.6;

    // 14) 매장 전체 대기시간 예측
    @Transactional(readOnly = true)
    public List<OrderWaitingTimeDto> getWaitingTimesForStore(Long storeId) {
        List<Order> queue = orderWaitingRepository.findActiveOrdersForStore(storeId);

        // 주문별 "남은 작업분" 계산
        Map<Long, Double> remainingMinutes = queue.stream().collect(Collectors.toMap(
                Order::getId,
                this::remainingMinutesForOrder
        ));

        // 누적합으로 "앞선 주문들의 남은 시간" + "내 주문 남은 시간"
        double acc = 0.0;
        List<OrderWaitingTimeDto> result = new ArrayList<>();
        for (Order o : queue) {
            double mine = remainingMinutes.getOrDefault(o.getId(), 0.0);
            double expected = (acc + mine) * CONCURRENCY_COEFF;
            int minutes = (int) Math.round(expected);
            result.add(OrderWaitingTimeDto.builder()
                    .orderId(o.getId())
                    .tableId(o.getTable() != null ? o.getTable().getId() : null)
                    .expectedWaitMinute(Math.max(0, minutes))
                    .build());
            acc += mine; // 다음 주문을 위해 누적
        }
        return result;
    }

    // 15) 개별 주문 대기시간 예측
    @Transactional(readOnly = true)
    public SingleWaitingTimeDto getWaitingTimeForOrder(Long orderId) {
        Order target = orderWaitingRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        Long storeId = target.getTable() != null ? target.getTable().getStore().getId() : null;
        if (storeId == null) throw new NotFoundException("Store not found for order: " + orderId);

        List<Order> queue = orderWaitingRepository.findActiveOrdersForStore(storeId);

        // 타깃 주문 이전까지의 누적 + 본인 남은 시간
        double acc = 0.0;
        double mine = remainingMinutesForOrder(target);
        for (Order o : queue) {
            if (Objects.equals(o.getId(), orderId)) break;
            acc += remainingMinutesForOrder(o);
        }
        double expected = (acc + mine) * CONCURRENCY_COEFF;
        int minutes = (int) Math.round(expected);

        return SingleWaitingTimeDto.builder()
                .orderId(orderId)
                .expectedWaitMinute(Math.max(0, minutes))
                .build();
    }

    // 주문의 "남은 작업분" (분) 계산
    private double remainingMinutesForOrder(Order o) {
        double base = 0.0;
        if (o.getOrderItems() != null) {
            for (OrderItem oi : o.getOrderItems()) {
                int qty = oi.getQuantity() != null ? oi.getQuantity() : 0;
                MenuItem mi = oi.getMenuItem();
                int prep = (mi != null && mi.getPrepTimeMin() != null) ? mi.getPrepTimeMin() : 3; // 기본 3분
                base += (double) prep * qty;
            }
        }
        return base * statusFactor(o.getStatus());
    }

}
