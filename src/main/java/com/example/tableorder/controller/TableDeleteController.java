package com.example.tableorder.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.tableorder.service.TableDeleteService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('OWNER') and @storeAccess.ownsTable(#tableId)")
@RequiredArgsConstructor
@RequestMapping("/api/tables")
public class TableDeleteController {

    private final TableDeleteService service;

    @DeleteMapping("/{table_id}")
    public ResponseEntity<?> delete(@PathVariable("table_id") Long tableId) {
        service.delete(tableId);
        return ResponseEntity.ok(Map.of("message", "Table deleted successfully."));
    }
}
