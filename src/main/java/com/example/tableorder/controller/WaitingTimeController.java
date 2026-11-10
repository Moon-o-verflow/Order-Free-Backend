package com.example.tableorder.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.tableorder.dto.waiting.OrderWaitingTimeDto;
import com.example.tableorder.dto.waiting.SingleWaitingTimeDto;
import com.example.tableorder.service.WaitingTimeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class WaitingTimeController {

    private final WaitingTimeService service;

    // 14) 매장 전체 대기시간 예측
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @GetMapping("/waiting-time")
    public ResponseEntity<List<OrderWaitingTimeDto>> storeWaitingTime(
            @RequestParam("store_id") Long storeId
    ) {
        return ResponseEntity.ok(service.getWaitingTimesForStore(storeId));
    }

    // 15) 주문별 대기시간 예측
    @PreAuthorize("hasRole('OWNER') and @storeAccess.ownsOrder(#orderId)")
    @GetMapping("/{order_id}/waiting-time")
    public ResponseEntity<SingleWaitingTimeDto> orderWaitingTime(
            @PathVariable("order_id") Long orderId
    ) {
        return ResponseEntity.ok(service.getWaitingTimeForOrder(orderId));
    }
}
