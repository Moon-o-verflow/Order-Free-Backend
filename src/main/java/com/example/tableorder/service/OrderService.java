package com.example.tableorder.service;

import com.example.tableorder.entity.order.OrderStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.tableorder.dto.MenuItemResponse;
import com.example.tableorder.dto.OrderHistoryResponse;
import com.example.tableorder.dto.OrderItemDetailResponse;
import com.example.tableorder.dto.OrderResponse;
import com.example.tableorder.entity.cart.Cart;
import com.example.tableorder.entity.cart.CartItem;
import com.example.tableorder.entity.order.Order;
import com.example.tableorder.entity.order.OrderItem;
import com.example.tableorder.entity.store.StoreTable;
import com.example.tableorder.repository.CartItemRepository;
import com.example.tableorder.repository.CartRepository;
import com.example.tableorder.repository.OrderRepository;
import com.example.tableorder.repository.StoreTableRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final StoreTableRepository storeTableRepository;
    private final CartRepository cartRepository;

    @Transactional
    public OrderResponse createOrder(Long storeId, Long tableId) {

        // 1. 테이블 조회
        StoreTable storeTable = storeTableRepository.findByStore_IdAndId(storeId, tableId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 테이블"));

        // 2. 장바구니 조회
        Cart cart = cartRepository.findByTable(storeTable)
                .orElseThrow(() -> new IllegalArgumentException("장바구니가 비어있습니다."));

        // 3. 장바구니 아이템들 조회
        List<CartItem> cartItems = cartItemRepository.findByCart(cart);

        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("주문할 상품이 없습니다.");
        }

        // 4. 총 금액 계산
        int totalAmount = cartItems.stream()
                .mapToInt(item -> item.getPrice() * item.getQuantity())
                .sum();

        // 5.Order 엔티티 생성
        Order order = Order.builder()
                .table(storeTable)
                .status(OrderStatus.ORDERED)
                .createdAt(LocalDateTime.now())
                .totalPrice(totalAmount) // 🔥 DB에 저장될 총액
                .orderItems(new ArrayList<>()) // 명시적으로 초기화
                .build();

        // 6. CartItem -> OrderItem으로 변환하여 추가
        for (CartItem cartItem : cartItems) {
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .menuItem(cartItem.getMenuItem())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(cartItem.getPrice())
                    .menuName(cartItem.getMenuName())
                    .build();
            order.addOrderItem(orderItem);
        }

        // 7. Order 저장
        Order savedOrder = orderRepository.save(order);

        // 8. 장바구니 아이템들 삭제
        cartItemRepository.deleteAll(cartItems);

        // 9. 빈 장바구니 삭제
        cartRepository.delete(cart);

        // 10. 반환
        return mapToOrderResponse(savedOrder);

    }

    private OrderResponse mapToOrderResponse(Order order) {
        // OrderItem들 OrderItemDetailResponse로 변환
        List<OrderItemDetailResponse> orderItemDetails = order.getOrderItems().stream()
                .map(this::mapToOrderItemDetailResponse)
                .toList();

        return OrderResponse.builder()
                .orderId(order.getId())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .totalPrice(order.getTotalPrice())
                .orderItems(orderItemDetails)
                .build();
    }

    private OrderItemDetailResponse mapToOrderItemDetailResponse(OrderItem orderItem) {
        // MenuItem을 MenuItemResponse로 변환
        MenuItemResponse menuResponse = MenuItemResponse.builder()
                .menuId(orderItem.getMenuItem().getId())
                .name(orderItem.getMenuName()) // 주문 시점의 메뉴명 사용
                .price(orderItem.getUnitPrice()) // 주문 시점의 가격 사용
                .imageUrl(orderItem.getMenuItem().getImageUrl())
                .soldOut(orderItem.getMenuItem().getSoldOut())
                .quantity(orderItem.getMenuItem().getQuantity())
                .enableInventory(orderItem.getMenuItem().getEnableInventory())
                .prepTimeMin(orderItem.getMenuItem().getPrepTimeMin())
                .options(new ArrayList<>()) // 현재는 옵션 없음
                .build();

        return OrderItemDetailResponse.builder()
                .menu(menuResponse)
                .quantity(orderItem.getQuantity())
                .options(new ArrayList<>()) // 현재는 옵션 없음
                .subtotal(orderItem.getUnitPrice() * orderItem.getQuantity())
                .build();
    }

    @Transactional
    public List<OrderHistoryResponse> getOrderHistory(Long storeId, Long tableId) {

        // 1. 테이블 조회 (유효성 검증용)
        storeTableRepository.findByStore_IdAndId(storeId, tableId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 테이블"));

        // 2. 12시간 이내 주문내역 조회
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(12);
        List<Order> orders = orderRepository.findAllByTable_Store_IdAndTable_IdAndCreatedAtAfter(storeId, tableId, cutoffTime);

        return mapToOrderHistoryResponse(orders);
    }

    private List<OrderHistoryResponse> mapToOrderHistoryResponse(List<Order> orders) {
        return orders.stream()
                .map(this::mapToOrderHistoryResponse)
                .toList();
    }

    private OrderHistoryResponse mapToOrderHistoryResponse(Order order) {
        // OrderItem을 OrderItemDetailResponse로 변환
        List<OrderItemDetailResponse> orderItemDetails = order.getOrderItems().stream()
                .map(this::mapToOrderItemDetailResponse)
                .toList();

        // 총 아이템 개수 계산
        int totalItemCount = order.getOrderItems().stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();

        // 총 금액 계산
        int totalAmount = order.getOrderItems().stream()
                .mapToInt(oi -> oi.getUnitPrice() * oi.getQuantity())
                .sum();

        return OrderHistoryResponse.builder()
                .orderId(order.getId())
                .orderTime(order.getCreatedAt())
                .orderStatus(order.getStatus())
                .totalItemCount(totalItemCount)
                .totalAmount(totalAmount)
                .orderItems(orderItemDetails)
                .build();
    }

}
