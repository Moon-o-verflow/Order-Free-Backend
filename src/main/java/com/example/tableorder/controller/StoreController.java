package com.example.tableorder.controller;

import com.example.tableorder.dto.StoreRequestDto;
import com.example.tableorder.dto.StoreResponseDto;
import com.example.tableorder.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
@Tag(name = "가게 관리", description = "가게 등록/조회/수정 API")
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "가게 등록", description = "사업자 번호 중복 확인 후 등록")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "등록 성공"),
        @ApiResponse(responseCode = "409", description = "사업자 번호 중복")
    })
    public ResponseEntity<StoreResponseDto> createStore(@Valid @RequestBody StoreRequestDto dto) {
        StoreResponseDto response = storeService.createStore(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "가게 목록 조회", description = "모든 가게 목록 조회 (메뉴판 접근용)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<java.util.List<StoreResponseDto>> getAllStores() {
        java.util.List<StoreResponseDto> response = storeService.getAllStores();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{storeId}")
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @Operation(summary = "가게 조회", description = "ID로 가게 정보 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "가게 없음")
    })
    public ResponseEntity<StoreResponseDto> getStore(@PathVariable Long storeId) {
        StoreResponseDto response = storeService.getStore(storeId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{storeId}")
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @Operation(summary = "가게 부분 수정", description = "부분 업데이트 지원")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "404", description = "가게 없음"),
        @ApiResponse(responseCode = "409", description = "사업자 번호 중복")
    })
    public ResponseEntity<StoreResponseDto> patchStore(@PathVariable Long storeId, @Valid @RequestBody StoreRequestDto dto) {
        StoreResponseDto response = storeService.patchStore(storeId, dto);
        return ResponseEntity.ok(response);
    }
}
