package com.example.tableorder.security;

import com.example.tableorder.config.JwtConfig;
import com.example.tableorder.config.SecurityConfig;
import com.example.tableorder.config.StoreAccessGuard;
import com.example.tableorder.controller.AdminMenuController;
import com.example.tableorder.controller.CartController;
import com.example.tableorder.controller.OrderController;
import com.example.tableorder.controller.OrderStatusController;
import com.example.tableorder.controller.StoreController;
import com.example.tableorder.controller.TableDeleteController;
import com.example.tableorder.dto.CartDetailResponse;
import com.example.tableorder.dto.order.UpdateOrderStatusResponse;
import com.example.tableorder.entity.order.OrderStatus;
import com.example.tableorder.repository.OrderRepository;
import com.example.tableorder.repository.PaymentRepository;
import com.example.tableorder.repository.StoreTableRepository;
import com.example.tableorder.service.AdminMenuService;
import com.example.tableorder.service.CartItemService;
import com.example.tableorder.service.OrderService;
import com.example.tableorder.service.OrderStatusService;
import com.example.tableorder.service.StoreService;
import com.example.tableorder.service.TableDeleteService;
import com.example.tableorder.support.JwtTokens;
import com.example.tableorder.util.EventBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AdminMenuController.class, CartController.class, OrderController.class,
        OrderStatusController.class, StoreController.class, TableDeleteController.class
})
@Import({SecurityConfig.class, JwtConfig.class, StoreAccessGuard.class})
@TestPropertySource(properties = {
        "spring.security.jwt.secret=" + SecurityAccessTest.SECRET,
        "spring.security.jwt.issuer=" + JwtTokens.ISSUER
})
class SecurityAccessTest {

    static final String SECRET = "test-secret-that-is-definitely-longer-than-32-bytes";

    @Autowired MockMvc mvc;

    @MockBean AdminMenuService adminMenuService;
    @MockBean CartItemService cartItemService;
    @MockBean OrderService orderService;
    @MockBean OrderStatusService orderStatusService;
    @MockBean StoreService storeService;
    @MockBean TableDeleteService tableDeleteService;
    @MockBean EventBroadcaster eventBroadcaster;
    @MockBean OrderRepository orderRepository;
    @MockBean StoreTableRepository storeTableRepository;
    @MockBean PaymentRepository paymentRepository;

    private String store1Owner;

    @BeforeEach
    void setUp() {
        store1Owner = "Bearer " + JwtTokens.owner(SECRET, "owner-1", 1L);
        when(orderRepository.findStoreIdById(10L)).thenReturn(Optional.of(1L));
        when(orderRepository.findStoreIdById(20L)).thenReturn(Optional.of(2L));
        when(storeTableRepository.findStoreIdById(5L)).thenReturn(Optional.of(1L));
        when(storeTableRepository.findStoreIdById(6L)).thenReturn(Optional.of(2L));
    }

    // ---- 고객 API는 토큰 없이 허용 ----

    @Test
    void customerCartIsPublic() throws Exception {
        when(cartItemService.getCart(1L, 5L))
                .thenReturn(CartDetailResponse.builder().items(List.of()).cartTotalPrice(0).build());
        mvc.perform(get("/api/stores/1/tables/5/carts")).andExpect(status().isOk());
    }

    @Test
    void storeListIsPublic() throws Exception {
        when(storeService.getAllStores()).thenReturn(List.of());
        mvc.perform(get("/api/stores")).andExpect(status().isOk());
    }

    // ---- 점주 API: 토큰 없음 → 401 ----

    @Test
    void ownerApiWithoutTokenIs401() throws Exception {
        mvc.perform(delete("/api/stores/1/menus/3"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        verify(adminMenuService, never()).deleteMenu(anyLong(), anyLong());
    }

    @Test
    void invalidTokenIs401() throws Exception {
        String forged = "Bearer " + JwtTokens.owner("another-secret-that-is-also-longer-than-32-bytes", "x", 1L);
        mvc.perform(delete("/api/stores/1/menus/3").header("Authorization", forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonOwnerRoleIs401() throws Exception {
        String customer = "Bearer " + JwtTokens.build(SECRET, JwtTokens.ISSUER, "c", "CUSTOMER", List.of(1L), 60_000);
        mvc.perform(delete("/api/stores/1/menus/3").header("Authorization", customer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIs401() throws Exception {
        String expired = "Bearer " + JwtTokens.build(SECRET, JwtTokens.ISSUER, "o", "OWNER", List.of(1L), -60_000);
        mvc.perform(delete("/api/stores/1/menus/3").header("Authorization", expired))
                .andExpect(status().isUnauthorized());
    }

    // ---- 점주 API: 소유권 ----

    @Test
    void ownerCanManageOwnStore() throws Exception {
        when(adminMenuService.deleteMenu(1L, 3L)).thenReturn("삭제 완료");
        mvc.perform(delete("/api/stores/1/menus/3").header("Authorization", store1Owner))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCannotManageOtherStore() throws Exception {
        mvc.perform(delete("/api/stores/2/menus/3").header("Authorization", store1Owner))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        verify(adminMenuService, never()).deleteMenu(anyLong(), anyLong());
    }

    @Test
    void orderStatusChecksOrderStore() throws Exception {
        when(orderStatusService.updateOrderStatus(eq(10L), anyString()))
                .thenReturn(UpdateOrderStatusResponse.builder().orderId(10L).status(OrderStatus.SERVED).build());
        String body = "{\"status\":\"SERVED\"}";

        mvc.perform(patch("/api/orders/10/status").header("Authorization", store1Owner)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/orders/20/status").header("Authorization", store1Owner)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/orders/999/status").header("Authorization", store1Owner)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void tableDeleteChecksTableStore() throws Exception {
        mvc.perform(delete("/api/tables/5").header("Authorization", store1Owner)).andExpect(status().isOk());
        mvc.perform(delete("/api/tables/6").header("Authorization", store1Owner)).andExpect(status().isForbidden());
        verify(tableDeleteService, never()).delete(6L);
    }

    @Test
    void outOfStockUnderCustomerPathStillNeedsOwner() throws Exception {
        String url = "/api/stores/1/tables/5/orders/menu/out-of-stock?menuId=3&menuName=x";
        mvc.perform(post(url)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/stores/2/tables/6/orders/menu/out-of-stock?menuId=3&menuName=x")
                .header("Authorization", store1Owner)).andExpect(status().isForbidden());
        mvc.perform(post(url).header("Authorization", store1Owner)).andExpect(status().isOk());
        verify(eventBroadcaster).publishToTable(eq("menu.out.of.stock"), any(), eq(1L), eq(5L));
    }

    @Test
    void createStoreNeedsOnlyOwnerRole() throws Exception {
        mvc.perform(post("/api/stores").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
