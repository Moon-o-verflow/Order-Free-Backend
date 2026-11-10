package com.example.tableorder.service;

import com.example.tableorder.dto.OrderResponse;
import com.example.tableorder.entity.cart.Cart;
import com.example.tableorder.entity.cart.CartItem;
import com.example.tableorder.entity.menu.MenuItem;
import com.example.tableorder.entity.order.Order;
import com.example.tableorder.entity.order.OrderItem;
import com.example.tableorder.entity.order.OrderStatus;
import com.example.tableorder.entity.store.StoreTable;
import com.example.tableorder.exception.ConflictException;
import com.example.tableorder.repository.AdminMenuRepository;
import com.example.tableorder.repository.CartItemRepository;
import com.example.tableorder.repository.CartRepository;
import com.example.tableorder.repository.OrderRepository;
import com.example.tableorder.repository.OrderStatusRepository;
import com.example.tableorder.repository.StoreTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryFlowTest {

    @Mock AdminMenuRepository adminMenuRepository;
    @Mock OrderRepository orderRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock StoreTableRepository storeTableRepository;
    @Mock CartRepository cartRepository;
    @Mock OrderStatusRepository orderStatusRepository;

    InventoryService inventory;
    OrderService orderService;
    OrderStatusService orderStatusService;

    StoreTable table = StoreTable.builder().id(5L).build();
    Cart cart = Cart.builder().id(1L).table(table).build();

    @BeforeEach
    void setUp() {
        inventory = new InventoryService(adminMenuRepository);
        orderService = new OrderService(orderRepository, cartItemRepository, storeTableRepository, cartRepository, inventory);
        orderStatusService = new OrderStatusService(orderStatusRepository, inventory);
    }

    private static MenuItem menu(long id, int price, boolean soldOut, boolean inventory, int qty) {
        return MenuItem.builder().id(id).price(price).soldOut(soldOut)
                .enableInventory(inventory).quantity(qty).prepTimeMin(5).build();
    }

    private void cartContains(CartItem... items) {
        when(storeTableRepository.findByStore_IdAndId(1L, 5L)).thenReturn(Optional.of(table));
        when(cartRepository.findByTable(table)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart(cart)).thenReturn(List.of(items));
    }

    private static CartItem cartItem(MenuItem menu, int qty, int priceAtAdd) {
        return CartItem.builder().menuItem(menu).quantity(qty).price(priceAtAdd).menuName("메뉴" + menu.getId()).build();
    }

    // ---- 확인 규칙 ----

    @Test
    void soldOutMenuIsRejected() {
        assertThatThrownBy(() -> inventory.checkOrderable(menu(1, 1000, true, false, 0), 1, "라면"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("품절");
    }

    @Test
    void insufficientStockIsRejected() {
        assertThatThrownBy(() -> inventory.checkOrderable(menu(1, 1000, false, true, 2), 3, "라면"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("남은 수량 2");
    }

    @Test
    void stockIgnoredWhenInventoryDisabled() {
        inventory.checkOrderable(menu(1, 1000, false, false, 0), 99, "라면");
    }

    // ---- 주문 생성 ----

    @Test
    void orderDeductsStockAndUsesCurrentPrice() {
        MenuItem tracked = menu(1, 1200, false, true, 10);   // 담은 뒤 가격이 1000 → 1200 으로 올랐다
        MenuItem untracked = menu(2, 500, false, false, 0);
        cartContains(cartItem(tracked, 3, 1000), cartItem(untracked, 2, 500));
        when(adminMenuRepository.deductStock(1L, 3)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse res = orderService.createOrder(1L, 5L);

        assertThat(res.getStatus()).isEqualTo(OrderStatus.ORDERED);
        assertThat(res.getTotalPrice()).isEqualTo(1200 * 3 + 500 * 2);
        verify(adminMenuRepository).deductStock(1L, 3);
        verify(adminMenuRepository, never()).deductStock(2L, 2);
        verify(cartRepository).delete(cart);
    }

    @Test
    void soldOutItemInCartBlocksOrder() {
        cartContains(cartItem(menu(1, 1000, true, false, 0), 1, 1000));

        assertThatThrownBy(() -> orderService.createOrder(1L, 5L)).isInstanceOf(ConflictException.class);
        verify(orderRepository, never()).save(any());
        verify(cartRepository, never()).delete(any());
    }

    @Test
    void lostRaceOnDeductBlocksOrder() {
        // 확인 시점에는 충분했지만 차감 직전에 다른 주문이 가져간 경우
        cartContains(cartItem(menu(1, 1000, false, true, 5), 5, 1000));
        when(adminMenuRepository.deductStock(1L, 5)).thenReturn(0);

        assertThatThrownBy(() -> orderService.createOrder(1L, 5L)).isInstanceOf(ConflictException.class);
        verify(orderRepository, never()).save(any());
    }

    // ---- 취소 시 복원 ----

    private Order orderWith(OrderStatus status, MenuItem menu, int qty) {
        Order order = Order.builder().id(77L).table(table).totalPrice(0).build();
        order.addOrderItem(OrderItem.builder().menuItem(menu).quantity(qty).unitPrice(menu.getPrice()).build());
        if (status == OrderStatus.PREPARING) order.changeStatus(OrderStatus.PREPARING);
        if (status == OrderStatus.CANCELLED) order.changeStatus(OrderStatus.CANCELLED);
        return order;
    }

    @Test
    void cancelRestoresStock() {
        Order order = orderWith(OrderStatus.PREPARING, menu(1, 1000, false, true, 0), 4);
        when(orderStatusRepository.findById(77L)).thenReturn(Optional.of(order));

        orderStatusService.updateOrderStatus(77L, "CANCELLED");

        verify(adminMenuRepository).restoreStock(1L, 4);
    }

    @Test
    void repeatedCancelDoesNotRestoreTwice() {
        Order order = orderWith(OrderStatus.CANCELLED, menu(1, 1000, false, true, 0), 4);
        when(orderStatusRepository.findById(77L)).thenReturn(Optional.of(order));

        orderStatusService.updateOrderStatus(77L, "CANCELLED");

        verify(adminMenuRepository, never()).restoreStock(anyLong(), anyInt());
    }

    @Test
    void cancelSkipsUntrackedMenus() {
        Order order = orderWith(OrderStatus.PREPARING, menu(2, 500, false, false, 0), 1);
        when(orderStatusRepository.findById(77L)).thenReturn(Optional.of(order));

        orderStatusService.updateOrderStatus(77L, "cancelled");

        verify(adminMenuRepository, never()).restoreStock(anyLong(), anyInt());
    }
}
