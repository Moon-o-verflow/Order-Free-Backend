package com.example.tableorder.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.tableorder.dto.floorplan.AutoTableMappingRequest;
import com.example.tableorder.service.FloorplanService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#request.storeId)")
@RequiredArgsConstructor
@RequestMapping("/api/floorplan")
public class FloorplanAutoMappingController {

    private final FloorplanService service;

    @PostMapping(path = "/auto-table-mapping", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> autoMapping(@RequestBody @Valid AutoTableMappingRequest request) {
        service.autoTableMapping(request);
        return ResponseEntity.ok(Map.of("message", "Auto table mapping completed."));
    }
}
