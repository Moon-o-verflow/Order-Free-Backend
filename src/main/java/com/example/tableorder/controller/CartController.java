package com.example.tableorder.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.tableorder.dto.CartDetailResponse;
import com.example.tableorder.dto.CartItemAddRequest;
import com.example.tableorder.dto.CartItemAddResponse;
import com.example.tableorder.service.CartItemService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stores/{storeId}/tables/{tableId}/carts")
@RequiredArgsConstructor
@Tag(name = "장바구니", description = "장바구니 관련 API")
public class CartController {

    private final CartItemService cartItemService;

    @PostMapping
    @Operation(
            summary = "장바구니에 아이템 추가",
            description = "특정 테이블의 장바구니에 메뉴 아이템을 추가합니다."
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "장바구니에 성공적으로 추가됨",
                content = @Content(schema = @Schema(implementation = CartItemAddResponse.class))
        ),
        @ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (메뉴 ID 없음, 수량 0 이하 등)"
        ),
        @ApiResponse(
                responseCode = "404",
                description = "테이블을 찾을 수 없음"
        )
    })
    public ResponseEntity<CartItemAddResponse> addCartItem(
            @Parameter(description = "매장 ID", example = "1") @PathVariable Long storeId,
            @Parameter(description = "테이블 ID", example = "5") @PathVariable Long tableId,
            @Parameter(description = "장바구니 추가 요청 정보") @RequestBody CartItemAddRequest request) {
        CartItemAddResponse response = cartItemService.cartItemCreate(storeId, tableId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
            summary = "장바구니 조회",
            description = "특정 테이블의 장바구니 내용을 조회합니다."
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "장바구니 조회 성공",
                content = @Content(schema = @Schema(implementation = CartDetailResponse.class))
        ),
        @ApiResponse(
                responseCode = "404",
                description = "테이블 또는 장바구니를 찾을 수 없음"
        )
    })
    public ResponseEntity<CartDetailResponse> getCartItem(
            @Parameter(description = "매장 ID", example = "1") @PathVariable Long storeId,
            @Parameter(description = "테이블 ID", example = "5") @PathVariable Long tableId) {
        CartDetailResponse response = cartItemService.getCart(storeId, tableId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{cartItemId}")
    @Operation(
            summary = "장바구니 아이템 삭제",
            description = "특정 테이블의 장바구니에서 메뉴 아이템을 삭제합니다."
    )
    @ApiResponses({
        @ApiResponse(
                responseCode = "204",
                description = "장바구니 아이템이 성공적으로 삭제됨"
        ),
        @ApiResponse(
                responseCode = "400",
                description = "잘못된 요청 (존재하지 않는 아이템, 권한 없음 등)"
        ),
        @ApiResponse(
                responseCode = "404",
                description = "테이블 또는 장바구니를 찾을 수 없음"
        )
    })
    public ResponseEntity<Void> deleteCartItem(
            @Parameter(description = "매장 ID", example = "1") @PathVariable Long storeId,
            @Parameter(description = "테이블 ID", example = "5") @PathVariable Long tableId,
            @Parameter(description = "삭제할 장바구니 아이템 ID", example = "10") @PathVariable Long cartItemId) {
        cartItemService.deleteCartItem(storeId, tableId, cartItemId);
        return ResponseEntity.noContent().build();
    }
}
