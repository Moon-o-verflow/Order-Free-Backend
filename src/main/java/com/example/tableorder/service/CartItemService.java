package com.example.tableorder.service;

import com.example.tableorder.exception.NotFoundException;

import com.example.tableorder.exception.BadRequestException;

import com.example.tableorder.dto.CartDetailResponse;
import com.example.tableorder.dto.CartItemAddRequest;
import com.example.tableorder.dto.CartItemAddResponse;
import com.example.tableorder.entity.cart.Cart;
import com.example.tableorder.entity.cart.CartItem;
import com.example.tableorder.entity.menu.MenuItem;
import com.example.tableorder.entity.menu.MenuItemI18n;
import com.example.tableorder.entity.store.StoreTable;
import com.example.tableorder.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final MenuRepository menuRepository;
    private final MenuItemI18nRepository menuItemI18nRepository;
    private final CartRepository cartRepository;
    private final StoreTableRepository storeTableRepository;
    private final InventoryService inventoryService;

    @Transactional
    public CartItemAddResponse cartItemCreate(Long storeId, Long tableId, CartItemAddRequest request) {

        // 입력검증 강화
        if (request.getMenuItemId() == null) {
            throw new BadRequestException("메뉴 아이템 ID는 필수입니다.");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BadRequestException("수량은 1 이상이어야 합니다.");
        }
        String lang = (request.getLang() == null || request.getLang().isBlank()) ? "ko" : request.getLang();

        // StoreTable 조회
        StoreTable storeTable = storeTableRepository.findByStore_IdAndId(storeId, tableId).orElseThrow(() -> new NotFoundException("존재하지 않는 테이블"));

        // 장바구니 조회, 없으면 생성
        Cart cart = cartRepository.findByTable(storeTable).orElseGet(() -> cartRepository.save(Cart.builder().table(storeTable).build()));

        // 메뉴 조회
        MenuItem menuItem = menuRepository.findByIdAndCategoryStoreId(request.getMenuItemId(),storeId).orElseThrow(() -> new NotFoundException("해당 매장의 메뉴가 존재하지 않습니다"));

        // 메뉴 가격 가져오기
        int price = menuItem.getPrice();

        // 언어 기준으로 이름 조회
        String name = menuItemI18nRepository.findByMenuItemIdAndLang(menuItem.getId(), lang).map(MenuItemI18n::getName).orElse("이름없음");

        // 기존 장바구니 아이템 조회 (중복 처리)
        CartItem existingCartItem = cartItemRepository.findByCartAndMenuItem(cart, menuItem).orElse(null);

        // 품절·재고 확인 (이미 담긴 수량 포함)
        int totalQuantity = request.getQuantity() + (existingCartItem != null ? existingCartItem.getQuantity() : 0);
        inventoryService.checkOrderable(menuItem, totalQuantity, name);

        CartItem saved;
        if (existingCartItem != null) {
            // 기존 아이템이 있으면 수량만 증가
            existingCartItem.setQuantity(existingCartItem.getQuantity() + request.getQuantity());
            saved = cartItemRepository.save(existingCartItem);
        } else {
            // 새 아이템 추가
            CartItem cartItem = CartItem.builder().cart(cart).menuItem(menuItem).quantity(request.getQuantity()).price(price).menuName(name).build();
            saved = cartItemRepository.save(cartItem);
        }

        return mapToCartItemAddResponse(saved);
    }

    private CartItemAddResponse mapToCartItemAddResponse(CartItem cartItem) {
        int q = cartItem.getQuantity();
        int p = cartItem.getPrice();
        return CartItemAddResponse.builder().cartItemId(cartItem.getId()).menuItemId(cartItem.getMenuItem().getId()).menuName(cartItem.getMenuName()).quantity(q).price(p).linePrice(p * q).build();
    }

    @Transactional
    public CartDetailResponse getCart(Long storeId, Long tableId) {
        StoreTable storeTable = storeTableRepository.findByStore_IdAndId(storeId, tableId).orElseThrow(() -> new NotFoundException("존재하지 않는 테이블"));

        // 아직 담은 적이 없거나(장바구니 없음) 비어 있으면 빈 장바구니로 응답
        List<CartItem> cartItems = cartRepository.findByTable(storeTable)
                .map(cartItemRepository::findByCart)
                .orElse(List.of());

        if (cartItems.isEmpty()) {
            return CartDetailResponse.builder().items(List.of()).cartTotalPrice(0).build();
        }

        return mapToCartDetailResponse(cartItems);
    }

    private CartDetailResponse mapToCartDetailResponse(List<CartItem> cartItems) {

        List<CartDetailResponse.Item> items = cartItems.stream().map(item -> CartDetailResponse.Item.builder().cartItemId(item.getId()).menuItemId(item.getMenuItem().getId()).menuName(item.getMenuName()).quantity(item.getQuantity()).price(item.getPrice()).linePrice(item.getQuantity() * item.getPrice()).build()).toList();

        int total = items.stream().mapToInt(CartDetailResponse.Item::getLinePrice).sum();

        return CartDetailResponse.builder().items(items).cartTotalPrice(total).build();
    }

    public void deleteCartItem(Long storeId, Long tableId, Long cartItemId) {
        // 1. 테이블 확인
        StoreTable storeTable = storeTableRepository.findByStore_IdAndId(storeId, tableId).orElseThrow(() -> new NotFoundException("존재하지 않는 테이블"));
        // 2. 장바구니 조회
        Cart cart = cartRepository.findByTable(storeTable).orElseThrow(() -> new NotFoundException("장바구니가 존재하지 않습니다"));
        // 3. 삭제할 아이템 조회 및 검증
        CartItem cartItem = cartItemRepository.findByIdAndCartId(cartItemId,cart.getId()).orElseThrow(() -> new NotFoundException("존재하지 않는 장바구니 아이템"));
        // 4. 삭제 실행
        cartItemRepository.delete(cartItem);
    }
}
