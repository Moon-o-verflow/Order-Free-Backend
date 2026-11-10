package com.example.tableorder.controller;

import com.example.tableorder.dto.OrderRequestDto;
import com.example.tableorder.dto.OrderResponseDto;
import com.example.tableorder.dto.OrderStatusUpdateDto;
import com.example.tableorder.service.AdminOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;  // import 추가

@RestController
@RequestMapping("/api/stores/{storeId}/orders")
@RequiredArgsConstructor
@Tag(name = "주문 관리", description = "점주용 주문 생성/목록/상태 변경 API")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @Operation(summary = "주문 목록 조회", description = "가게 내 모든 주문, 최신순")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "가게 없음")
    })
    public ResponseEntity<List<OrderResponseDto>> listOrders(@PathVariable Long storeId) {
        List<OrderResponseDto> response = adminOrderService.listOrders(storeId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @Operation(summary = "주문 생성", description = "재고 차감 및 가격 스냅샷")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "409", description = "재고 부족")
    })
    public ResponseEntity<OrderResponseDto> createOrder(
            @PathVariable Long storeId,
            @Valid @RequestBody OrderRequestDto dto) {
        OrderResponseDto response = adminOrderService.createOrder(storeId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @Operation(summary = "주문 상태 변경", description = "합법 전이만 허용")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 전이"),
            @ApiResponse(responseCode = "404", description = "주문 없음")
    })
    public ResponseEntity<OrderResponseDto> changeStatus(
            @PathVariable Long storeId,
            @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateDto dto) {
        OrderResponseDto response = adminOrderService.changeStatus(storeId, orderId, dto);
        return ResponseEntity.ok(response);
    }
}