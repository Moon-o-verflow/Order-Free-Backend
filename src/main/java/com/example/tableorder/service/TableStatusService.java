package com.example.tableorder.service;

import com.example.tableorder.dto.table.UpdateTableStatusResponse;
import com.example.tableorder.entity.store.StoreTable;
import com.example.tableorder.exception.BadRequestException;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.repository.StoreTableRepository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TableStatusService {

    private static final Set<String> ALLOWED = Set.of("Empty", "Occupied", "Paying");

    private final StoreTableRepository storeTableRepository;

    @Transactional
    public UpdateTableStatusResponse updateStatus(Long tableId, String status) {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("status is required");
        }
        if (!ALLOWED.contains(status)) {
            throw new BadRequestException("Invalid status. Allowed: " + ALLOWED);
        }

        StoreTable t = storeTableRepository.findById(tableId)
                .orElseThrow(() -> new NotFoundException("Table not found: " + tableId));

        t.setStatus(status); // 변경감지 업데이트

        return UpdateTableStatusResponse.builder()
                .tableId(t.getId())
                .status(t.getStatus())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
