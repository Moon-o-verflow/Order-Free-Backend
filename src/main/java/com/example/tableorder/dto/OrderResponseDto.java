package com.example.tableorder.dto;

import com.example.tableorder.entity.order.OrderStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class OrderResponseDto {

    private Long id;

    private Long tableId;

    private OrderStatus status;

    private Integer totalPrice;

    private LocalDateTime createdAt;  // Instant → LocalDateTime

    private List<OrderItemResponseDto> items;
}