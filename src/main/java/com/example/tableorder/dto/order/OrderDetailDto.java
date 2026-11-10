package com.example.tableorder.dto.order;

import com.example.tableorder.entity.order.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderDetailDto {
    private Long orderId;
    private Long tableId;
    private OrderStatus status;
    private LocalDateTime orderedAt; // createdAt
    private List<OrderItemSummaryDto> orderItems;
    private Integer totalAmount;
    private String paymentStatus;    // "Paid" | "Unpaid"
}
