package com.example.tableorder.dto.order;

import com.example.tableorder.entity.order.OrderStatus;

import java.time.LocalDateTime;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateOrderStatusResponse {
    private Long orderId;
    private OrderStatus status;
    private LocalDateTime updatedAt;
}
