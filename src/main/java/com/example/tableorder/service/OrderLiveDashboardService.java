package com.example.tableorder.service;

import com.example.tableorder.dto.order.LiveOrderDto;
import com.example.tableorder.repository.OrderRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderLiveDashboardService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<LiveOrderDto> getLiveOrders(Long storeId) {
        return orderRepository.findLiveOrders(storeId);
    }
}
