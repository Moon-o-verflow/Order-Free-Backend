package com.example.tableorder.service.payment;

import com.example.tableorder.dto.payment.PaymentResponse;
import com.example.tableorder.entity.payment.PaymentStatus;
import com.example.tableorder.exception.ConflictException;
import com.example.tableorder.exception.NotFoundException;
import com.example.tableorder.support.MySqlTestcontainersConfig;
import com.example.tableorder.support.PaymentFixtures;
import com.example.tableorder.support.PaymentFixtures.TableRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-1 결제 준비를 실제 MySQL(UNIQUE 제약 포함)에서 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfig.class)
class PaymentPrepareMySqlTest {

    @Autowired PaymentFacade facade;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    TableRef table;

    @BeforeEach
    void setUp() {
        table = PaymentFixtures.newTable(jdbc);
    }

    @Test
    void prepareCreatesReadyPaymentWithServerAmount() {
        long orderId = PaymentFixtures.newOrder(jdbc, table, 23000, "ORDERED");

        PaymentTxService.PrepareResult r = facade.prepare(table.storeId(), table.tableId(), orderId);

        assertThat(r.created()).isTrue();
        assertThat(r.payment().status()).isEqualTo(PaymentStatus.READY);
        assertThat(r.payment().amount()).isEqualTo(23000);
        assertThat(r.payment().pgOrderId()).matches("OF-" + orderId + "-[A-Za-z0-9]{16}");
    }

    @Test
    void repeatedPrepareReturnsExistingWithoutNewRow() {
        long orderId = PaymentFixtures.newOrder(jdbc, table, 10000, "ORDERED");

        PaymentResponse first = facade.prepare(table.storeId(), table.tableId(), orderId).payment();
        PaymentTxService.PrepareResult again = facade.prepare(table.storeId(), table.tableId(), orderId);

        assertThat(again.created()).isFalse();
        assertThat(again.payment().pgOrderId()).isEqualTo(first.pgOrderId());
        assertThat(PaymentFixtures.paymentRows(jdbc, orderId)).isEqualTo(1);
    }

    @Test
    void concurrentFirstPreparesCreateExactlyOneRow() throws Exception {
        long orderId = PaymentFixtures.newOrder(jdbc, table, 10000, "ORDERED");
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentTxService.PrepareResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<PaymentTxService.PrepareResult> call = () -> {
                start.await();
                return facade.prepare(table.storeId(), table.tableId(), orderId);
            };
            futures.add(pool.submit(call));
        }
        start.countDown();

        List<PaymentTxService.PrepareResult> results = new ArrayList<>();
        for (Future<PaymentTxService.PrepareResult> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();

        assertThat(PaymentFixtures.paymentRows(jdbc, orderId)).isEqualTo(1);
        assertThat(results).filteredOn(PaymentTxService.PrepareResult::created).hasSize(1);
        assertThat(results).extracting(r -> r.payment().pgOrderId()).containsOnly(results.get(0).payment().pgOrderId());
    }

    @Test
    void orderOfAnotherTableIsNotFound() {
        long orderId = PaymentFixtures.newOrder(jdbc, table, 10000, "ORDERED");
        TableRef other = PaymentFixtures.newTable(jdbc);

        assertThatThrownBy(() -> facade.prepare(other.storeId(), other.tableId(), orderId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancelledOrderCannotBePaid() {
        long orderId = PaymentFixtures.newOrder(jdbc, table, 10000, "CANCELLED");
        assertThatThrownBy(() -> facade.prepare(table.storeId(), table.tableId(), orderId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void failedPaymentIsReissuedWithNewPgOrderId() {
        long orderId = PaymentFixtures.newOrder(jdbc, table, 10000, "ORDERED");
        String firstPgOrderId = facade.prepare(table.storeId(), table.tableId(), orderId).payment().pgOrderId();
        jdbc.update("UPDATE payment SET status='FAILED', failure_code='REJECT_CARD_COMPANY' WHERE order_id=?", orderId);

        PaymentResponse retried = facade.prepare(table.storeId(), table.tableId(), orderId).payment();

        assertThat(retried.status()).isEqualTo(PaymentStatus.READY);
        assertThat(retried.pgOrderId()).isNotEqualTo(firstPgOrderId);
        assertThat(PaymentFixtures.paymentRows(jdbc, orderId)).isEqualTo(1);
    }

    @Test
    void databaseRejectsSecondPaymentRowAndDuplicatePaymentKey() {
        long orderA = PaymentFixtures.newOrder(jdbc, table, 10000, "ORDERED");
        long orderB = PaymentFixtures.newOrder(jdbc, table, 10000, "ORDERED");
        String insert = "INSERT INTO payment (order_id, pg_order_id, payment_key, amount, status, created_at, updated_at)"
                + " VALUES (?, ?, ?, 10000, 'DONE', NOW(), NOW())";
        jdbc.update(insert, orderA, "OF-a-1", "pk-same");

        // 같은 주문에 두 번째 결제 행
        assertThatThrownBy(() -> jdbc.update(insert, orderA, "OF-a-2", "pk-other"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 같은 PG 승인(payment_key)이 다른 결제에 기록
        assertThatThrownBy(() -> jdbc.update(insert, orderB, "OF-b-1", "pk-same"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void customerEndpointsArePublic() throws Exception {
        long orderId = PaymentFixtures.newOrder(jdbc, table, 12000, "ORDERED");
        String url = "/api/stores/%d/tables/%d/orders/%d/payments".formatted(table.storeId(), table.tableId(), orderId);

        mvc.perform(post(url)).andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(12000));
        mvc.perform(post(url)).andExpect(status().isOk());

        String pgOrderId = jdbc.queryForObject("SELECT pg_order_id FROM payment WHERE order_id=?", String.class, orderId);
        mvc.perform(get("/api/payments/" + pgOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        mvc.perform(get("/api/payments/OF-unknown-0000000000000000")).andExpect(status().isNotFound());
    }
}
