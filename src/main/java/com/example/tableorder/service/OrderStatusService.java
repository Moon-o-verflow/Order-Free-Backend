package com.example.tableorder.service;

import com.example.tableorder.dto.order.UpdateOrderStatusResponse;
import com.example.tableorder.entity.order.Order;
import com.example.tableorder.entity.order.OrderStatus;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.repository.OrderStatusRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderStatusService {

    private final OrderStatusRepository orderStatusRepository;

    @Transactional
    public UpdateOrderStatusResponse updateOrderStatus(Long orderId, String status) {
        OrderStatus next = OrderStatus.from(status);
        Order o = orderStatusRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        o.changeStatus(next); // 변경감지로 업데이트

        return UpdateOrderStatusResponse.builder()
                .orderId(o.getId())
                .status(o.getStatus())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
