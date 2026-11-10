package com.example.tableorder.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.tableorder.dto.OrderHistoryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.tableorder.dto.OrderResponse;
import com.example.tableorder.dto.realtime.OrderStatusChangedEvent;
import com.example.tableorder.dto.realtime.MenuOutOfStockEvent;
import com.example.tableorder.service.OrderService;
import com.example.tableorder.util.EventBroadcaster;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stores/{storeId}/tables/{tableId}/orders")
@RequiredArgsConstructor
@Tag(name = "주문", description = "주문 관련 API")
public class OrderController {

    private final OrderService orderService;
    private final EventBroadcaster eventBroadcaster;

    @PostMapping
    @Operation(
            summary = "주문 생성",
            description = "장바구니에 있는 상품들을 주문으로 생성합니다. 주문 후 장바구니는 자동으로 비워집니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "주문이 성공적으로 생성됨",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (장바구니가 비어있음, 주문할 상품이 없음 등)"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "테이블을 찾을 수 없음"
            )
    })
    public ResponseEntity<OrderResponse> createOrder(
            @Parameter(description = "매장 ID", example = "1") @PathVariable Long storeId,
            @Parameter(description = "테이블 ID", example = "5") @PathVariable Long tableId) {

        OrderResponse response = orderService.createOrder(storeId, tableId);

        // 실시간 이벤트 전송: 주문 상태 변경 (주문 접수)
        OrderStatusChangedEvent orderEvent = OrderStatusChangedEvent.builder()
                .storeId(storeId)
                .tableId(tableId)
                .orderId(response.getOrderId())
                .orderStatus(response.getStatus().label())
                .userName("사용자") // 실제로는 JWT에서 사용자 정보 추출
                .timestamp(System.currentTimeMillis())
                .message("새로운 주문이 접수되었습니다")
                .build();

        // 해당 테이블에만 이벤트 전송
        eventBroadcaster.publishToTable("order.status.changed", orderEvent, storeId, tableId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
            summary = "주문 히스토리 조회",
            description = "특정 테이블의 최근 12시간 이내 주문 히스토리를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "주문 히스토리 조회 성공",
                    content = @Content(schema = @Schema(implementation = OrderHistoryResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "테이블을 찾을 수 없음"
            )
    })
    public ResponseEntity<List<OrderHistoryResponse>> getOrderHistory(
            @Parameter(description = "매장 ID", example = "1") @PathVariable Long storeId,
            @Parameter(description = "테이블 ID", example = "5") @PathVariable Long tableId) {

        List<OrderHistoryResponse> response = orderService.getOrderHistory(storeId, tableId);
        return ResponseEntity.ok(response);
    }

    /**
     * 메뉴 품절 알림 (관리자용 API)
     */
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @PostMapping("/menu/out-of-stock")
    @Operation(
            summary = "메뉴 품절 알림",
            description = "특정 메뉴가 품절되었음을 실시간으로 알립니다."
    )
    public ResponseEntity<Void> notifyMenuOutOfStock(
            @Parameter(description = "매장 ID", example = "1") @PathVariable Long storeId,
            @Parameter(description = "테이블 ID", example = "5") @PathVariable Long tableId,
            @Parameter(description = "품절된 메뉴 ID") @RequestParam Long menuId,
            @Parameter(description = "품절된 메뉴명") @RequestParam String menuName) {

        MenuOutOfStockEvent outOfStockEvent = MenuOutOfStockEvent.builder()
                .storeId(storeId)
                .menuId(menuId)
                .menuName(menuName)
                .message("오늘 " + menuName + "이 품절되었습니다")
                .timestamp(System.currentTimeMillis())
                .build();

        // 해당 테이블에만 이벤트 전송
        eventBroadcaster.publishToTable("menu.out.of.stock", outOfStockEvent, storeId, tableId);

        return ResponseEntity.ok().build();
    }
}
