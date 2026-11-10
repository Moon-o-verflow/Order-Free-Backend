package com.example.tableorder.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.tableorder.dto.table.CreateTableRequest;
import com.example.tableorder.dto.table.CreateTableResponse;
import com.example.tableorder.service.TableCreateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#request.storeId)")
@RequiredArgsConstructor
@RequestMapping("/api/tables")
public class TableCreateController {

    private final TableCreateService service;

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<CreateTableResponse> create(
            @RequestBody @Valid CreateTableRequest request
    ) {
        CreateTableResponse res = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }
}
