package com.example.tableorder.service;

import com.example.tableorder.dto.table.TableListItemDto;
import com.example.tableorder.entity.store.StoreTable;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.repository.StoreRepository;
import com.example.tableorder.repository.StoreTableRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TableQueryService {

    private final StoreRepository storeRepository;
    private final StoreTableRepository storeTableRepository;

    @Transactional(readOnly = true)
    public List<TableListItemDto> listTables(Long storeId) {
        // 스토어 존재 확인 (없으면 404)
        storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("Store not found: " + storeId));

        return storeTableRepository.findByStore_IdOrderByTableNumberAsc(storeId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private TableListItemDto toDto(StoreTable t) {
        return TableListItemDto.builder()
                .tableId(t.getId())
                .tableNumber(t.getTableNumber())
                .status(t.getStatus())
                .build();
    }
}
