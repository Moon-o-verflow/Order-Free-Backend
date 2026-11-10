package com.example.tableorder.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.tableorder.dto.table.UpdateTableStatusRequest;
import com.example.tableorder.dto.table.UpdateTableStatusResponse;
import com.example.tableorder.service.TableStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('OWNER') and @storeAccess.ownsTable(#tableId)")
@RequiredArgsConstructor
@RequestMapping("/api/tables")
public class TableStatusController {

    private final TableStatusService service;

    @PatchMapping(path = "/{table_id}/status", consumes = "application/json", produces = "application/json")
    public ResponseEntity<UpdateTableStatusResponse> updateStatus(
            @PathVariable("table_id") Long tableId,
            @RequestBody @Valid UpdateTableStatusRequest request
    ) {
        return ResponseEntity.ok(service.updateStatus(tableId, request.getStatus()));
    }
}
