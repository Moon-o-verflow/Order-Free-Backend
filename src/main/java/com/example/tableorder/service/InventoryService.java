package com.example.tableorder.service;

import com.example.tableorder.entity.menu.MenuItem;
import com.example.tableorder.entity.order.Order;
import com.example.tableorder.entity.order.OrderItem;
import com.example.tableorder.exception.ConflictException;
import com.example.tableorder.repository.AdminMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메뉴 품절·재고 규칙.
 * - 품절 메뉴는 담거나 주문할 수 없다.
 * - 재고 관리(enableInventory) 메뉴는 주문 시 원자적으로 차감하고, 주문 취소 시 복원한다.
 *
 * 호출하는 쪽 트랜잭션 안에서만 쓴다. 차감 실패 시 예외로 전체 주문이 롤백된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class InventoryService {

    private final AdminMenuRepository adminMenuRepository;

    /** 담기·주문 전 확인. 수량은 이번에 필요한 총 수량. */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public void checkOrderable(MenuItem menu, int quantity, String displayName) {
        if (Boolean.TRUE.equals(menu.getSoldOut())) {
            throw new ConflictException("품절된 메뉴입니다: " + displayName);
        }
        if (Boolean.TRUE.equals(menu.getEnableInventory()) && menu.getQuantity() < quantity) {
            throw new ConflictException("재고가 부족합니다: " + displayName + " (남은 수량 " + menu.getQuantity() + ")");
        }
    }

    /** 주문 확정 시 재고 차감. 재고 관리 메뉴가 아니면 품절 여부만 확인한다. */
    public void deduct(MenuItem menu, int quantity, String displayName) {
        checkOrderable(menu, quantity, displayName);
        if (Boolean.TRUE.equals(menu.getEnableInventory())
                && adminMenuRepository.deductStock(menu.getId(), quantity) == 0) {
            // 확인과 차감 사이에 다른 주문이 먼저 가져간 경우
            throw new ConflictException("재고가 부족합니다: " + displayName);
        }
    }

    /** 주문 취소 시 재고 복원. */
    public void restore(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            MenuItem menu = item.getMenuItem();
            if (menu != null && Boolean.TRUE.equals(menu.getEnableInventory())) {
                adminMenuRepository.restoreStock(menu.getId(), item.getQuantity());
            }
        }
    }
}
