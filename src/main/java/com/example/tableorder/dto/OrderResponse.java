package com.example.tableorder.dto;

import com.example.tableorder.entity.order.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "주문 응답 정보")
public class OrderResponse {

    @NotNull
    @Schema(description = "주문 ID", example = "123")
    private Long orderId;

    @NotNull
    @Schema(description = "주문 상태", example = "ORDERED")
    private OrderStatus status;

    @NotNull
    @Schema(description = "주문 생성 시간", example = "2024-01-15T14:30:00")
    private LocalDateTime createdAt;

    @NotNull
    @Schema(description = "총 주문 금액 (원)", example = "15000")
    private Integer totalPrice;

    @Schema(description = "주문 아이템 목록")
    private List<OrderItemDetailResponse> orderItems;
}
