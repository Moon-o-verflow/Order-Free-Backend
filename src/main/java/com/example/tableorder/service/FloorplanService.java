package com.example.tableorder.service;

import com.example.tableorder.dto.floorplan.*;
import com.example.tableorder.entity.store.Store;
import com.example.tableorder.entity.store.StoreTable;
import com.example.tableorder.exception.BadRequestException;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.repository.StoreRepository;
import com.example.tableorder.repository.StoreTableRepository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FloorplanService {

    private final StoreRepository storeRepository;
    private final StoreTableRepository storeTableRepository;

    // 11) 평면도 정보 조회
    @Transactional(readOnly = true)
    public FloorplanResponse getFloorplan(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("Store not found: " + storeId));

        List<TablePositionDto> tables = storeTableRepository.findByStore_IdOrderByTableNumberAsc(storeId)
                .stream()
                .map(t -> TablePositionDto.builder()
                        .tableId(t.getId())
                        .coordX(t.getPositionX())
                        .coordY(t.getPositionY())
                        .build())
                .collect(Collectors.toList());

        return FloorplanResponse.builder()
                .floorplanUrl(store.getFloorplanUrl())
                .tables(tables)
                .build();
    }

    // 12) 자동 테이블 매핑
    @Transactional
    public void autoTableMapping(AutoTableMappingRequest req) {
        // 스토어 존재 확인
        storeRepository.findById(req.getStoreId())
                .orElseThrow(() -> new NotFoundException("Store not found: " + req.getStoreId()));

        // 좌표 일괄 업데이트
        for (AutoTableMappingRequest.TableLayoutItem item : req.getTableLayout()) {
            StoreTable t = storeTableRepository.findById(item.getTableId())
                    .orElseThrow(() -> new NotFoundException("Table not found: " + item.getTableId()));
            if (!t.getStore().getId().equals(req.getStoreId())) {
                throw new BadRequestException("Table " + t.getId() + " does not belong to store " + req.getStoreId());
            }
            t.setPositionX(item.getCoordX());
            t.setPositionY(item.getCoordY());
            // 변경감지로 저장
        }
    }

    // 13) 테이블 위치 수정
    @Transactional
    public UpdateTablePositionResponse updateTablePosition(Long tableId, Integer x, Integer y) {
        StoreTable t = storeTableRepository.findById(tableId)
                .orElseThrow(() -> new NotFoundException("Table not found: " + tableId));
        t.setPositionX(x);
        t.setPositionY(y);
        return UpdateTablePositionResponse.builder()
                .tableId(t.getId())
                .coordX(t.getPositionX())
                .coordY(t.getPositionY())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
