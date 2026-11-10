package com.example.tableorder.service;

import com.example.tableorder.dto.order.*;
import com.example.tableorder.entity.order.*;
import com.example.tableorder.entity.payment.OrderPaymentStatus;
import com.example.tableorder.entity.payment.Payment;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.repository.OrderDetailRepository;
import com.example.tableorder.repository.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderDetailService {

    private final OrderDetailRepository orderDetailRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public OrderDetailDto getOrderDetail(Long orderId) {
        Order o = orderDetailRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        Long tableId = (o.getTable() != null) ? o.getTable().getId() : null;

        List<OrderItemSummaryDto> items = o.getOrderItems() == null ? List.of() :
                o.getOrderItems().stream().map(oi -> OrderItemSummaryDto.builder()
                        .menuId(oi.getMenuItem() != null ? oi.getMenuItem().getId() : null)
                        .name(oi.getMenuName() != null ? oi.getMenuName()
                                : (oi.getMenuItem() != null ? oi.getMenuItem().getId().toString() : null))
                        .qty(oi.getQuantity())
                        .unitPrice(oi.getUnitPrice())
                        .build()
                ).collect(Collectors.toList());

        int total = items.stream().mapToInt(i -> i.getUnitPrice() * i.getQty()).sum();

        OrderPaymentStatus paymentStatus = OrderPaymentStatus.of(
                paymentRepository.findByOrder_Id(orderId).map(Payment::getStatus).orElse(null));

        return OrderDetailDto.builder()
                .orderId(o.getId())
                .tableId(tableId)
                .status(o.getStatus())
                .orderedAt(o.getCreatedAt())
                .orderItems(items)
                .totalAmount(total)
                .paymentStatus(paymentStatus)
                .build();
    }
}
