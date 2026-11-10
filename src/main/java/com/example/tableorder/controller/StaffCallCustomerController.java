package com.example.tableorder.controller;

import com.example.tableorder.dto.StaffCallRequest;
import com.example.tableorder.dto.StaffCallResponse;
import com.example.tableorder.dto.StaffCallTypeResponse;
import com.example.tableorder.service.StaffCallCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/customer/stores/{storeId}")
@RequiredArgsConstructor
@Tag(name= "직원 호출(손님)", description = "직원 호출 관련 API(손님)")
public class StaffCallCustomerController {
    private final StaffCallCustomerService staffCallCustomerService;

    @GetMapping("/staff-call-types")
    @Operation(summary="직원 호출 목록 조회(손님)", description = "해당 매장의 직원 호출 타입을 조회합니다.(손님)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "직원 호출 목록 조회 성공"),
            @ApiResponse(responseCode = "404", description = "해당 매장을 찾을 수 없음")
    })
    public ResponseEntity<List<StaffCallTypeResponse>> getStaffCallTypes(@PathVariable("storeId") Long storeId) {
        List<StaffCallTypeResponse> responses = staffCallCustomerService.getStaffCallTypes(storeId);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/staff-call")
    @Operation(summary="직원 호출 요청(손님)", description = "손님이 해당 매장의 직원 호출을 요청합니다.")
    @ApiResponses({
            @ApiResponse(responseCode= "201",description ="직원 호출이 성공적으로 생성됨"),
            @ApiResponse(responseCode="400",description="잘못된 요청 데이터"),
            @ApiResponse(responseCode="404",description = "해당 매장을 찾을 수 없음")
    })
    public ResponseEntity<StaffCallResponse> doStaffCall(@PathVariable("storeId") Long storeId, @Valid @RequestBody StaffCallRequest request){
        StaffCallResponse response = staffCallCustomerService.doStaffCall(storeId,request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


}
