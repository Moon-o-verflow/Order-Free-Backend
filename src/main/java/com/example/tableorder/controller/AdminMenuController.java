package com.example.tableorder.controller;

import com.example.tableorder.dto.AdminMenuRequestDto;
import com.example.tableorder.dto.AdminMenuResponseDto;
import com.example.tableorder.service.AdminMenuService;
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
 * 점주 파트 메뉴 관리 컨트롤러.
 * - 메뉴 등록/수정/삭제 엔드포인트 제공.
 */
@RestController
@RequestMapping("/api/stores/{storeId}/menus")
@RequiredArgsConstructor
@Tag(name = "메뉴 관리", description = "점주용 메뉴 등록/수정/삭제 API")
public class AdminMenuController {

    private final AdminMenuService adminMenuService;

    @PostMapping("/{menuId}")
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @Operation(summary = "메뉴 등록", description = "클라이언트 제공 menuId로 등록 (중복 시 409)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "409", description = "메뉴 ID 중복")
    })
    public ResponseEntity<AdminMenuResponseDto> createMenu(
            @PathVariable Long storeId,
            @PathVariable Long menuId,
            @Valid @RequestBody AdminMenuRequestDto dto) {
        AdminMenuResponseDto response = adminMenuService.createMenu(storeId, menuId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{menuId}")
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @Operation(summary = "메뉴 부분 수정", description = "부분 업데이트 지원")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "메뉴 없음")
    })
    public ResponseEntity<AdminMenuResponseDto> patchMenu(
            @PathVariable Long storeId,
            @PathVariable Long menuId,
            @Valid @RequestBody AdminMenuRequestDto dto) {
        AdminMenuResponseDto response = adminMenuService.patchMenu(storeId, menuId, dto);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{menuId}")
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @Operation(summary = "메뉴 삭제", description = "FK 제약 위반 시 409")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "404", description = "메뉴 없음"),
            @ApiResponse(responseCode = "409", description = "삭제 불가 (활성 주문 참조)")
    })
    public ResponseEntity<String> deleteMenu(
            @PathVariable Long storeId,
            @PathVariable Long menuId) {
        String message = adminMenuService.deleteMenu(storeId, menuId);
        return ResponseEntity.ok(message);
    }
}