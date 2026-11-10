package com.example.tableorder.service;

import com.example.tableorder.dto.table.CreateTableRequest;
import com.example.tableorder.dto.table.CreateTableResponse;
import com.example.tableorder.entity.store.Store;
import com.example.tableorder.entity.store.StoreTable;
import com.example.tableorder.exception.BadRequestException;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.repository.StoreRepository;
import com.example.tableorder.repository.StoreTableRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TableCreateService {

    private final StoreRepository storeRepository;
    private final StoreTableRepository storeTableRepository;

    @Transactional
    public CreateTableResponse create(CreateTableRequest req) {
        Store store = storeRepository.findById(req.getStoreId())
                .orElseThrow(() -> new NotFoundException("Store not found: " + req.getStoreId()));

        // 같은 매장 내 tableNumber 중복 방지
        if (storeTableRepository.existsByStore_IdAndTableNumber(store.getId(), req.getTableNumber())) {
            throw new BadRequestException("table_number already exists in the store");
        }

        StoreTable t = StoreTable.builder()
                .store(store)
                .tableNumber(req.getTableNumber())
                .status("Empty") // 기본값
                .build();

        StoreTable saved = storeTableRepository.save(t);

        return CreateTableResponse.builder()
                .tableId(saved.getId())
                .storeId(store.getId())
                .tableNumber(saved.getTableNumber())
                .status(saved.getStatus())
                .build();
    }
}
