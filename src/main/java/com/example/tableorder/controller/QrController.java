package com.example.tableorder.controller;

import com.example.tableorder.dto.QrCodeRequestDto;
import com.example.tableorder.dto.QrCodeResponseDto;
import com.example.tableorder.service.QrService;
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

/**
 * QR 코드 관리 컨트롤러.
 * - 생성/조회 엔드포인트 제공.
 */
@RestController
@RequestMapping("/api/stores/{storeId}/qrcodes/{tableId}")
@RequiredArgsConstructor
@Tag(name = "QR 관리", description = "테이블 QR 생성/조회 API")
public class QrController {

    private final QrService qrService;

    @PostMapping
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @Operation(summary = "QR 생성", description = "캐시 미스 시 생성, 히트 시 반환")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "404", description = "테이블 없음")
    })
    public ResponseEntity<QrCodeResponseDto> createQrCode(
            @PathVariable Long storeId,
            @PathVariable Long tableId,
            @Valid @RequestBody QrCodeRequestDto dto) {
        QrCodeResponseDto response = qrService.getOrCreateQrCode(storeId, tableId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @Operation(summary = "QR 조회", description = "캐시 우선 반환")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "테이블 없음")
    })
    public ResponseEntity<QrCodeResponseDto> getQrCode(
            @PathVariable Long storeId,
            @PathVariable Long tableId,
            @RequestBody QrCodeRequestDto dto) {  // GET이지만 body 허용 (optional 파라미터)
        QrCodeResponseDto response = qrService.getOrCreateQrCode(storeId, tableId, dto);
        return ResponseEntity.ok(response);
    }
}