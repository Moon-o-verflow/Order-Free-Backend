package com.example.tableorder.repository;

import com.example.tableorder.support.MySqlTestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL 전용 재고 쿼리(SET 절 왼쪽부터 평가)를 실제 MySQL에서 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestcontainersConfig.class)
class StockQueriesMySqlTest {

    @Autowired AdminMenuRepository repo;
    @Autowired JdbcTemplate jdbc;

    long categoryId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO store (business_number, name) VALUES ('stock-test', '재고 테스트')");
        Long storeId = jdbc.queryForObject("SELECT id FROM store WHERE business_number='stock-test'", Long.class);
        jdbc.update("INSERT INTO category (store_id, name_ko) VALUES (?, '테스트')", storeId);
        categoryId = jdbc.queryForObject("SELECT MAX(id) FROM category", Long.class);
    }

    private long menu(int qty, boolean inventory, boolean soldOut) {
        jdbc.update("INSERT INTO menu_item (category_id, price, quantity, enable_inventory, sold_out) VALUES (?,1000,?,?,?)",
                categoryId, qty, inventory, soldOut);
        return jdbc.queryForObject("SELECT MAX(id) FROM menu_item", Long.class);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT quantity, sold_out FROM menu_item WHERE id=?", id);
    }

    private static boolean soldOut(Map<String, Object> row) {
        return ((Boolean) row.get("sold_out"));
    }

    @Test
    void deductKeepsAvailableWhenStockRemains() {
        long id = menu(5, true, false);
        assertThat(repo.deductStock(id, 3)).isEqualTo(1);
        assertThat(row(id).get("quantity")).isEqualTo(2);
        assertThat(soldOut(row(id))).isFalse();
    }

    @Test
    void deductToZeroMarksSoldOut() {
        long id = menu(3, true, false);
        assertThat(repo.deductStock(id, 3)).isEqualTo(1);
        assertThat(row(id).get("quantity")).isEqualTo(0);
        assertThat(soldOut(row(id))).isTrue();
    }

    @Test
    void deductRejectsInsufficientSoldOutOrUntracked() {
        assertThat(repo.deductStock(menu(2, true, false), 3)).isZero();
        assertThat(repo.deductStock(menu(5, true, true), 1)).isZero();
        assertThat(repo.deductStock(menu(5, false, false), 1)).isZero();
    }

    @Test
    void restoreFromZeroClearsAutoSoldOut() {
        long id = menu(0, true, true);
        assertThat(repo.restoreStock(id, 2)).isEqualTo(1);
        assertThat(row(id).get("quantity")).isEqualTo(2);
        assertThat(soldOut(row(id))).isFalse();
    }

    @Test
    void restoreKeepsManualSoldOutWhenStockWasLeft() {
        long id = menu(4, true, true);
        repo.restoreStock(id, 1);
        assertThat(row(id).get("quantity")).isEqualTo(5);
        assertThat(soldOut(row(id))).isTrue();
    }

    @Test
    void adminDeltaNoLongerDoubleCounts() {
        // 예전 버그: 5 - 3 = 2 인데 sold_out 계산에서 delta를 한 번 더 빼서 품절 처리됨
        long id = menu(5, true, false);
        assertThat(repo.updateQuantityAtomically(id, -3)).isEqualTo(1);
        assertThat(row(id).get("quantity")).isEqualTo(2);
        assertThat(soldOut(row(id))).isFalse();

        assertThat(repo.updateQuantityAtomically(id, -2)).isEqualTo(1);
        assertThat(soldOut(row(id))).isTrue();
    }
}
