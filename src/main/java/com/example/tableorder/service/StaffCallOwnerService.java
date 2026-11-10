package com.example.tableorder.service;

import com.example.tableorder.exception.NotFoundException;

import com.example.tableorder.dto.StaffCallTypeRequest;
import com.example.tableorder.dto.StaffCallTypeResponse;
import com.example.tableorder.entity.staffcall.StaffCallType;
import com.example.tableorder.entity.store.Store;
import com.example.tableorder.repository.StaffCallTypeRepository;
import com.example.tableorder.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffCallOwnerService {

    private final StoreRepository storeRepository;
    private final StaffCallTypeRepository staffCallTypeRepository;

    // 점주의 직원호출 종류 만들기
    public StaffCallTypeResponse staffCallTypeCreate(Long storeId, StaffCallTypeRequest request) {
        // 1. 매장 정보 가져오기
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("해당 매장이 존재하지 않습니다."));
        // 2. 매장 메세지 등록하기 / 활성화
        StaffCallType staffCallType = StaffCallType.builder()
                .store(store)
                .message(request.getMessage())
                .active(true)
                .build();
        StaffCallType saved = staffCallTypeRepository.save(staffCallType);
        return mapToStaffCallTypeResponse(saved);
    }

    private StaffCallTypeResponse mapToStaffCallTypeResponse(StaffCallType staffCallType) {
        return StaffCallTypeResponse.builder()
                .id(staffCallType.getId())
                .message(staffCallType.getMessage())
                .active(staffCallType.isActive())
                .build();
    }

    // 직원호출 삭제
    public void deleteStaffCallType(Long storeId, Long callTypeId) {
        // 매장 존재 여부 확인
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("해당 매장이 존재하지 않습니다."));

        // 호출 타입 존재 여부 확인
        StaffCallType staffCallType = staffCallTypeRepository.findById(callTypeId)
                .orElseThrow(() -> new NotFoundException("해당 호출 타입이 존재하지 않습니다."));

        // 매장 검증 (보안 차원: 다른 매장 데이터 삭제 못 하게)
        if (!staffCallType.getStore().getId().equals(store.getId())) {
            throw new NotFoundException("이 호출 타입은 해당 매장에 속하지 않습니다.");
        }

        staffCallTypeRepository.delete(staffCallType);
    }

    public StaffCallTypeResponse staffCallTypeUpdate(Long storeId, Long callTypeId, StaffCallTypeRequest request) {
        // 1. 매장 존재 여부 확인
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("해당 매장이 존재하지 않습니다."));

        // 2. 호출 타입 존재 여부 확인
        StaffCallType staffCallType = staffCallTypeRepository.findById(callTypeId)
                .orElseThrow(() -> new NotFoundException("해당 호출 타입이 존재하지 않습니다."));

        // 3. 매장 검증 (보안 차원: 다른 매장 데이터 수정 못 하게)
        if (!staffCallType.getStore().getId().equals(store.getId())) {
            throw new NotFoundException("이 호출 타입은 해당 매장에 속하지 않습니다.");
        }

        // 4. 메시지 업데이트
        staffCallType.setMessage(request.getMessage());

        // 5. 저장 및 응답 반환
        StaffCallType saved = staffCallTypeRepository.save(staffCallType);
        return mapToStaffCallTypeResponse(saved);
    }

    // 매장의 모든 직원호출 타입 조회
    public List<StaffCallTypeResponse> getStaffCallTypes(Long storeId) {
        // 1. 매장 존재 여부 확인
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("해당 매장이 존재하지 않습니다."));

        // 2. 해당 매장의 모든 직원호출 타입 조회
        List<StaffCallType> staffCallTypes = staffCallTypeRepository.findByStore(store);

        // 3. 응답 DTO로 변환하여 반환
        return staffCallTypes.stream()
                .map(this::mapToStaffCallTypeResponse)
                .toList();
    }
}
