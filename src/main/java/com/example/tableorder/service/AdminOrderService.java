package com.example.tableorder.service;

import com.example.tableorder.entity.order.OrderStatus;

import com.example.tableorder.dto.OrderRequestDto;
import com.example.tableorder.dto.OrderResponseDto;
import com.example.tableorder.dto.OrderStatusUpdateDto;
import com.example.tableorder.dto.OrderItemResponseDto;
import com.example.tableorder.entity.menu.MenuItem;
import com.example.tableorder.entity.menu.MenuItemI18n;
import com.example.tableorder.entity.menu.MenuOption;
import com.example.tableorder.entity.order.Order;
import com.example.tableorder.entity.order.OrderItem;
import com.example.tableorder.entity.order.OrderItemOption;
import com.example.tableorder.entity.store.StoreTable;
import com.example.tableorder.exception.BusinessRuleException;
import com.example.tableorder.exception.ConflictException;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.repository.AdminMenuRepository;
import com.example.tableorder.repository.AdminOrderRepository;
import com.example.tableorder.repository.MenuItemI18nRepository;
import com.example.tableorder.repository.MenuOptionRepository;  // 추가
import com.example.tableorder.repository.OrderItemRepository;
import com.example.tableorder.repository.StoreTableRepository;
import com.example.tableorder.util.EventBroadcaster;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 점주용 주문 관리 서비스.
 * - 주문 생성/목록/상태 변경 처리.
 * - 가격 스냅샷, 재고 차감, 전이 규칙 적용.
 */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderService.class);

    private final AdminOrderRepository adminOrderRepository;
    private final StoreTableRepository storeTableRepository;
    private final AdminMenuRepository adminMenuRepository;
    private final MenuOptionRepository menuOptionRepository;  // 추가 주입
    private final OrderItemRepository orderItemRepository;
    private final MenuItemI18nRepository menuItemI18nRepository;
    private final EventBroadcaster eventBroadcaster;

    /**
     * 주문 목록 조회.
     * - storeId 기준, 최신순 정렬.
     * @param storeId 가게 ID
     * @return List<OrderResponseDto>
     */
    @Transactional(readOnly = true)
    public List<OrderResponseDto> listOrders(Long storeId) {
        List<Order> orders = adminOrderRepository.findByStoreIdWithItems(storeId);  // Fetch Join 쿼리 호출로 변경
        return orders.stream().map(this::toOrderResponseDto).collect(Collectors.toList());
    }

    /**
     * 주문 생성.
     * - 가격 스냅샷, 재고 차감, 초기 상태 PREPARING.
     * @param storeId 가게 ID
     * @param dto 요청 DTO
     * @return OrderResponseDto
     */
    @Transactional
    public OrderResponseDto createOrder(Long storeId, OrderRequestDto dto) {
        StoreTable table = storeTableRepository.findByStore_IdAndId(storeId, dto.getTableId())
                .orElseThrow(() -> new NotFoundException("테이블 없음"));

        Order order = Order.builder()
                .table(table)
                .status(OrderStatus.ORDERED)
                .totalPrice(0)
                .createdAt(LocalDateTime.now())
                .build();

        List<OrderItem> orderItems = new ArrayList<>();
        int totalPrice = 0;

        for (OrderRequestDto.Item itemDto : dto.getItems()) {
            MenuItem menuItem = adminMenuRepository.findById(itemDto.getMenuItemId())
                    .orElseThrow(() -> new NotFoundException("메뉴 없음"));

            if (menuItem.getEnableInventory() && menuItem.getQuantity() < itemDto.getQuantity()) {
                throw new ConflictException("재고 부족");
            }

            int unitPrice = menuItem.getPrice();
            if (itemDto.getOptionIds() != null) {
                unitPrice += itemDto.getOptionIds().stream()
                        .map(id -> menuOptionRepository.findById(id)  // AdminMenuRepository → MenuOptionRepository 변경
                                .map(opt -> opt.getExtraPrice())  // 람다로 변경
                                .orElse(0))
                        .mapToInt(i -> i)  // Integer::intValue → i -> i (안전성)
                        .sum();
            }

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .menuItem(menuItem)
                    .quantity(itemDto.getQuantity())
                    .unitPrice(unitPrice)
                    .menuName(menuItemI18nRepository.findByMenuItemIdAndLang(menuItem.getId(), "ko")
                            .map(i -> i.getName())
                            .orElse("이름없음"))
                    .build();

            orderItems.add(orderItem);
            totalPrice += unitPrice * itemDto.getQuantity();

            if (menuItem.getEnableInventory()) {
                int rowsUpdated = adminMenuRepository.updateQuantityAtomically(menuItem.getId(), -itemDto.getQuantity());
                if (rowsUpdated == 0) {
                    throw new ConflictException("재고 차감 실패");
                }
            }
        }

        order.setTotalPrice(totalPrice);
        order = adminOrderRepository.save(order);
        orderItemRepository.saveAll(orderItems);

        log.info("주문 생성: storeId={}, orderId={}", storeId, order.getId());
        eventBroadcaster.publish("ws.orders." + storeId + ".updated");

        return toOrderResponseDto(order);
    }

    /**
     * 주문 상태 변경.
     * - 허용 전이는 {@link OrderStatus#nextStatuses()} 참고.
     * @param storeId 가게 ID
     * @param orderId 주문 ID
     * @param dto 상태 DTO
     * @return OrderResponseDto
     */
    @Transactional
    public OrderResponseDto changeStatus(Long storeId, Long orderId, OrderStatusUpdateDto dto) {
        Order order = adminOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("주문 없음"));

        if (!order.getTable().getStore().getId().equals(storeId)) {
            throw new BusinessRuleException("주문 소속 불일치");
        }

        OrderStatus newStatus = OrderStatus.from(dto.getStatus());
        order.changeStatus(newStatus);

        log.info("주문 상태 변경: storeId={}, orderId={}, newStatus={}", storeId, orderId, newStatus);
        eventBroadcaster.publish("ws.orders." + storeId + ".status.changed");

        return toOrderResponseDto(order);
    }

    private OrderResponseDto toOrderResponseDto(Order order) {
        return OrderResponseDto.builder()
                .id(order.getId())
                .tableId(order.getTable().getId())
                .status(order.getStatus())
                .totalPrice(order.getTotalPrice())
                .createdAt(order.getCreatedAt())
                .items(order.getOrderItems().stream()
                        .map(item -> OrderItemResponseDto.builder()
                                .id(item.getId())
                                .menuItemId(item.getMenuItem().getId())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .menuName(item.getMenuName())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}