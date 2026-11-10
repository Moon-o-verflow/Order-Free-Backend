package com.example.tableorder.service;

import com.example.tableorder.exception.NotFoundException;


import com.example.tableorder.dto.StaffCallRequest;
import com.example.tableorder.dto.StaffCallResponse;
import com.example.tableorder.dto.StaffCallTypeResponse;
import com.example.tableorder.entity.staffcall.StaffCall;
import com.example.tableorder.entity.staffcall.StaffCallType;
import com.example.tableorder.entity.store.Store;
import com.example.tableorder.entity.store.StoreTable;
import com.example.tableorder.repository.StaffCallRepository;
import com.example.tableorder.repository.StaffCallTypeRepository;
import com.example.tableorder.repository.StoreRepository;
import com.example.tableorder.repository.StoreTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffCallCustomerService {
    private final StoreRepository storeRepository;
    private final StaffCallTypeRepository staffCallTypeRepository;
    private final StaffCallRepository staffCallRepository;
    private final StoreTableRepository storeTableRepository;

    private StaffCallTypeResponse mapToStaffCallTypeResponse(StaffCallType staffCallType) {
        return StaffCallTypeResponse.builder()
                .id(staffCallType.getId())
                .message(staffCallType.getMessage())
                .active(staffCallType.isActive())
                .build();

    }

    // 손님이 직원호출 종류 조회
    public List<StaffCallTypeResponse> getStaffCallTypes(Long storeId){
        // 1. 매장 존재여부 확인
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("해당 매장이 존재하지 않습니다."));

        // 2. 해당 매장의 모든 직원호출 타입 조회 (점주와 다르게 활성화된 것만 가져옴)
        List<StaffCallType> staffCallTypes = staffCallTypeRepository.findByStoreAndActiveTrue(store);

        // 3. 응답 DTO로 변환하여 응답
        return staffCallTypes.stream()
                .map(this::mapToStaffCallTypeResponse)
                .toList();
    }

    public StaffCallResponse doStaffCall(Long storeId, StaffCallRequest request){

        // 1. 해당 매장 조회
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("해당 매장이 존재하지 않습니다."));

        // 2. 해당 매장의 직원 호출 타입 조회
        StaffCallType staffCallType = staffCallTypeRepository.findByStoreAndId(store,request.getCallTypeId())
                .orElseThrow(() -> new NotFoundException("해당 매장의 호출 타입이 존재하지 않습니다."));

        // 3. 테이블 조회 (손님이 요청한 tableId가 그 매장에 속하는지 확인)
        StoreTable table = storeTableRepository.findByStore_IdAndId(storeId, request.getTableId())
                .orElseThrow(() -> new NotFoundException("해당 매장의 테이블이 존재하지 않습니다."));

        // 4. staffcall 엔티티 저장
        StaffCall staffCall = StaffCall.builder()
                .table(table)
                .callType(staffCallType)
                .requestedAt(LocalDateTime.now())
                .build();

        StaffCall savedStaffCall = staffCallRepository.save(staffCall);

        // 5. 응답 DTO로 변환하여 반환
        return mapToStaffCallResponse(savedStaffCall);
    }

    private StaffCallResponse mapToStaffCallResponse(StaffCall staffCall) {
        return StaffCallResponse.builder()
                .id(staffCall.getId())
                .tableId(staffCall.getTable().getId())
                .message(staffCall.getCallType().getMessage())
                .requestedAt(staffCall.getRequestedAt())
                .build();
    }
}
