package com.example.tableorder.service;

import com.example.tableorder.entity.store.StoreTable;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.repository.StoreTableRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TableDeleteService {

    private final StoreTableRepository storeTableRepository;

    @Transactional
    public void delete(Long tableId) {
        StoreTable t = storeTableRepository.findById(tableId)
                .orElseThrow(() -> new NotFoundException("Table not found: " + tableId));
        storeTableRepository.delete(t);
    }
}
