package com.example.tableorder.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.tableorder.dto.table.TableListItemDto;
import com.example.tableorder.service.TableQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
@RequiredArgsConstructor
@RequestMapping("/api/tables")
public class TableQueryController {

    private final TableQueryService service;

    @GetMapping
    public ResponseEntity<List<TableListItemDto>> list(
            @RequestParam("store_id") Long storeId
    ) {
        return ResponseEntity.ok(service.listTables(storeId));
    }
}
