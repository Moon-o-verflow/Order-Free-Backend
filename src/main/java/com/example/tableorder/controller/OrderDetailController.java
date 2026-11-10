package com.example.tableorder.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.tableorder.dto.order.OrderDetailDto;
import com.example.tableorder.service.OrderDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('OWNER') and @storeAccess.ownsOrder(#orderId)")
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderDetailController {

    private final OrderDetailService service;

    @GetMapping("/{order_id}")
    public ResponseEntity<OrderDetailDto> getOrderDetail(
            @PathVariable("order_id") Long orderId
    ) {
        return ResponseEntity.ok(service.getOrderDetail(orderId));
    }
}
