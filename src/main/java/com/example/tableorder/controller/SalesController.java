package com.example.tableorder.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.tableorder.dto.sales.*;
import com.example.tableorder.service.SalesService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
@RequiredArgsConstructor
@RequestMapping("/api/sales")
public class SalesController {

    private final SalesService service;

    // 4) 기간별 매출 요약
    @GetMapping("/summary")
    public ResponseEntity<List<SalesByDateDto>> summary(
            @RequestParam("store_id") Long storeId,
            @RequestParam("period_type") String periodType,
            @RequestParam("start") String start,
            @RequestParam("end") String end
    ) {
        return ResponseEntity.ok(service.getSalesSummary(storeId, periodType, start, end));
    }

    // 5) 메뉴별 매출 집계
    @GetMapping("/by-menu")
    public ResponseEntity<List<SalesByMenuDto>> byMenu(
            @RequestParam("store_id") Long storeId,
            @RequestParam("start") String start,
            @RequestParam("end") String end
    ) {
        return ResponseEntity.ok(service.getSalesByMenu(storeId, start, end));
    }

    // 6) 테이블별 매출 집계 (단일 날짜)
    @GetMapping("/by-table")
    public ResponseEntity<List<SalesByTableDto>> byTable(
            @RequestParam("store_id") Long storeId,
            @RequestParam("date") String date
    ) {
        return ResponseEntity.ok(service.getSalesByTable(storeId, date));
    }
}
