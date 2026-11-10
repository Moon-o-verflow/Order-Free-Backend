package com.example.tableorder.controller;

import com.example.tableorder.dto.CategoryRequestDto;
import com.example.tableorder.dto.CategoryResponseDto;
import com.example.tableorder.service.CategoryService;
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
@RequestMapping("/api/stores/{storeId}/categories")
@RequiredArgsConstructor
@Tag(name = "카테고리 관리", description = "카테고리 추가 API")
public class CategoryController {

    private final CategoryService categoryService;

    @PatchMapping
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @Operation(summary = "카테고리 추가", description = "가게에 카테고리 추가 (중복 허용)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가 성공"),
            @ApiResponse(responseCode = "404", description = "가게 없음")
    })
    public ResponseEntity<CategoryResponseDto> addCategory(
            @PathVariable Long storeId,
            @Valid @RequestBody CategoryRequestDto dto) {
        CategoryResponseDto response = categoryService.addCategory(storeId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}