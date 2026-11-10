package com.example.tableorder.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.tableorder.dto.order.UpdateOrderStatusRequest;
import com.example.tableorder.dto.order.UpdateOrderStatusResponse;
import com.example.tableorder.service.OrderStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('OWNER') and @storeAccess.ownsOrder(#orderId)")
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderStatusController {

    private final OrderStatusService service;

    @PatchMapping("/{order_id}/status")
    public ResponseEntity<UpdateOrderStatusResponse> updateStatus(
            @PathVariable("order_id") Long orderId,
            @RequestBody @Valid UpdateOrderStatusRequest request
    ) {
        return ResponseEntity.ok(service.updateOrderStatus(orderId, request.getStatus()));
    }
}
