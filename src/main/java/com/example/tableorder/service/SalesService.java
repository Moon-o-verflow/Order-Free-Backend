package com.example.tableorder.service;

import com.example.tableorder.dto.sales.*;
import com.example.tableorder.exception.BadRequestException;
import com.example.tableorder.repository.SalesRepository;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesService {

    private final SalesRepository salesRepository;

    // 4) 기간별 매출 요약
    @Transactional(readOnly = true)
    public List<SalesByDateDto> getSalesSummary(Long storeId, String periodType, String start, String end) {
        if (!"Day".equalsIgnoreCase(periodType)) {
            throw new BadRequestException("Only period_type=Day is supported for now");
        }
        LocalDateTime[] range = parseRange(start, end);
        return salesRepository.sumByDate(storeId, range[0], range[1]);
    }

    // 5) 메뉴별 매출 집계
    @Transactional(readOnly = true)
    public List<SalesByMenuDto> getSalesByMenu(Long storeId, String start, String end) {
        LocalDateTime[] range = parseRange(start, end);
        return salesRepository.sumByMenu(storeId, range[0], range[1]);
    }

    // 6) 테이블별 매출 집계 (단일 날짜)
    @Transactional(readOnly = true)
    public List<SalesByTableDto> getSalesByTable(Long storeId, String date) {
        LocalDate d = parseDate(date);
        LocalDateTime start = d.atStartOfDay();
        LocalDateTime end = d.plusDays(1).atStartOfDay(); // [start, end)
        return salesRepository.sumByTableForDate(storeId, start, end);
    }

    private LocalDateTime[] parseRange(String start, String end) {
        LocalDate s = parseDate(start);
        LocalDate e = parseDate(end);
        if (e.isBefore(s)) throw new BadRequestException("end must be >= start");
        // [start 00:00, end 23:59:59.999999999] 대신 [start, nextDayOfEnd)로 안전 처리
        return new LocalDateTime[] { s.atStartOfDay(), e.plusDays(1).atStartOfDay() };
    }

    private LocalDate parseDate(String x) {
        try {
            return LocalDate.parse(x); // ISO yyyy-MM-dd
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Invalid date format. Use YYYY-MM-DD");
        }
    }
}
