package com.example.tableorder.service;

import com.example.tableorder.dto.StoreRequestDto;
import com.example.tableorder.dto.StoreResponseDto;
import com.example.tableorder.entity.store.Store;
import com.example.tableorder.exception.ConflictException;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가게 관리 서비스. - 등록/조회/부분 수정 처리. - 사업자 번호 중복 검증.
 */
@Service
@RequiredArgsConstructor
public class StoreService {

    private static final Logger log = LoggerFactory.getLogger(StoreService.class);

    private final StoreRepository storeRepository;

    /**
     * 가게 등록. - 사업자 번호 중복 검증.
     *
     * @param dto 요청 DTO
     * @return StoreResponseDto
     */
    @Transactional
    public StoreResponseDto createStore(StoreRequestDto dto) {
        if (storeRepository.findByBusinessNumber(dto.getBusinessNumber()).isPresent()) {
            throw new ConflictException("사업자 번호 중복");
        }

        Store store = Store.builder()
                .businessNumber(dto.getBusinessNumber())
                .name(dto.getName())
                .address(dto.getAddress())
                .hours(dto.getHours())
                .floorplanUrl(dto.getFloorplanUrl())
                .build();

        storeRepository.save(store);
        log.info("가게 등록: id={}, businessNumber={}", store.getId(), store.getBusinessNumber());

        return toStoreResponseDto(store);
    }

    /**
     * 모든 가게 목록 조회 (메뉴판 접근용).
     *
     * @return List<StoreResponseDto>
     */
    public java.util.List<StoreResponseDto> getAllStores() {
        java.util.List<Store> stores = storeRepository.findAll();
        log.info("가게 목록 조회: 총 {}개", stores.size());
        return stores.stream()
                .map(this::toStoreResponseDto)
                .toList();
    }

    /**
     * 가게 조회.
     *
     * @param storeId 가게 ID
     * @return StoreResponseDto
     */
    public StoreResponseDto getStore(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("가게 없음"));
        return toStoreResponseDto(store);
    }

    /**
     * 가게 부분 수정. - 사업자 번호 변경 시 중복 검증. - null 필드 무시.
     *
     * @param storeId 가게 ID
     * @param dto 요청 DTO
     * @return StoreResponseDto
     */
    @Transactional
    public StoreResponseDto patchStore(Long storeId, StoreRequestDto dto) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("가게 없음"));

        if (dto.getBusinessNumber() != null && !dto.getBusinessNumber().equals(store.getBusinessNumber())) {
            if (storeRepository.findByBusinessNumber(dto.getBusinessNumber()).isPresent()) {
                throw new ConflictException("사업자 번호 중복");
            }
            store.setBusinessNumber(dto.getBusinessNumber());
        }
        if (dto.getName() != null) {
            store.setName(dto.getName());
        }
        if (dto.getAddress() != null) {
            store.setAddress(dto.getAddress());
        }
        if (dto.getHours() != null) {
            store.setHours(dto.getHours());
        }
        if (dto.getFloorplanUrl() != null) {
            store.setFloorplanUrl(dto.getFloorplanUrl());
        }

        storeRepository.save(store);
        log.info("가게 수정: id={}, businessNumber={}", store.getId(), store.getBusinessNumber());

        return toStoreResponseDto(store);
    }

    private StoreResponseDto toStoreResponseDto(Store store) {
        return StoreResponseDto.builder()
                .id(store.getId())
                .businessNumber(store.getBusinessNumber())
                .name(store.getName())
                .address(store.getAddress())
                .hours(store.getHours())
                .floorplanUrl(store.getFloorplanUrl())
                .build();
    }
}
