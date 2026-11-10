package com.example.tableorder.config;

import com.example.tableorder.repository.OrderRepository;
import com.example.tableorder.repository.StoreTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 매장 소유권 검사. {@code @PreAuthorize("@storeAccess.owns(#storeId)")} 형태로 사용한다.
 *
 * 대상(주문·테이블)이 없으면 false를 반환한다. 다른 매장 리소스의 존재 여부가
 * 404/403 차이로 드러나지 않도록 하기 위함이다.
 */
@Component("storeAccess")
@RequiredArgsConstructor
public class StoreAccessGuard {

    private final OrderRepository orderRepository;
    private final StoreTableRepository storeTableRepository;

    public boolean owns(Long storeId) {
        OwnerPrincipal owner = currentOwner();
        return owner != null && owner.owns(storeId);
    }

    public boolean ownsOrder(Long orderId) {
        OwnerPrincipal owner = currentOwner();
        return owner != null && orderId != null
                && orderRepository.findStoreIdById(orderId).map(owner::owns).orElse(false);
    }

    public boolean ownsTable(Long tableId) {
        OwnerPrincipal owner = currentOwner();
        return owner != null && tableId != null
                && storeTableRepository.findStoreIdById(tableId).map(owner::owns).orElse(false);
    }

    private static OwnerPrincipal currentOwner() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof OwnerPrincipal p ? p : null;
    }
}
