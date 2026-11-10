package com.example.tableorder.dto.order;

import com.example.tableorder.entity.order.OrderStatus;

import java.time.LocalDateTime;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LiveOrderDto {
    private Long orderId;
    private Long tableId;
    private OrderStatus status;
    private LocalDateTime orderedAt; // Order.createdAt
    private Long totalAmount;     // sum(unitPrice * quantity)
}
