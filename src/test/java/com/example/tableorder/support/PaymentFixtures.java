package com.example.tableorder.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/** 실제 MySQL 테스트용 매장·테이블·주문 데이터. 테스트마다 새 매장을 만들어 서로 섞이지 않게 한다. */
public final class PaymentFixtures {

    public record TableRef(long storeId, long tableId) {
    }

    private PaymentFixtures() {
    }

    public static TableRef newTable(JdbcTemplate jdbc) {
        String biz = UUID.randomUUID().toString().substring(0, 18);
        jdbc.update("INSERT INTO store (business_number, name) VALUES (?, '결제 테스트 매장')", biz);
        long storeId = jdbc.queryForObject("SELECT id FROM store WHERE business_number=?", Long.class, biz);
        jdbc.update("INSERT INTO store_table (store_id, table_number) VALUES (?, 1)", storeId);
        long tableId = jdbc.queryForObject("SELECT id FROM store_table WHERE store_id=?", Long.class, storeId);
        return new TableRef(storeId, tableId);
    }

    public static long newOrder(JdbcTemplate jdbc, TableRef t, int totalPrice, String status) {
        jdbc.update("INSERT INTO `order` (table_id, status, total_price) VALUES (?, ?, ?)", t.tableId(), status, totalPrice);
        return jdbc.queryForObject("SELECT MAX(id) FROM `order` WHERE table_id=?", Long.class, t.tableId());
    }

    public static int paymentRows(JdbcTemplate jdbc, long orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE order_id=?", Integer.class, orderId);
    }
}
