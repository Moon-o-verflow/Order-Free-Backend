package com.example.tableorder.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.tableorder.dto.floorplan.UpdateTablePositionRequest;
import com.example.tableorder.dto.floorplan.UpdateTablePositionResponse;
import com.example.tableorder.service.FloorplanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('OWNER') and @storeAccess.ownsTable(#tableId)")
@RequiredArgsConstructor
@RequestMapping("/api/floorplan")
public class FloorplanUpdateController {

    private final FloorplanService service;

    @PatchMapping(path = "/table/{table_id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<UpdateTablePositionResponse> update(
            @PathVariable("table_id") Long tableId,
            @RequestBody @Valid UpdateTablePositionRequest request
    ) {
        return ResponseEntity.ok(service.updateTablePosition(tableId, request.getCoordX(), request.getCoordY()));
    }
}
