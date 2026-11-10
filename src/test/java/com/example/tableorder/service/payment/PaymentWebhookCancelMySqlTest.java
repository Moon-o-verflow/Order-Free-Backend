package com.example.tableorder.service.payment;

import com.example.tableorder.dto.order.OrderDetailDto;
import com.example.tableorder.dto.payment.PaymentResponse;
import com.example.tableorder.dto.payment.TossWebhookRequest;
import com.example.tableorder.entity.payment.OrderPaymentStatus;
import com.example.tableorder.entity.payment.PaymentStatus;
import com.example.tableorder.exception.ConflictException;
import com.example.tableorder.service.OrderDetailService;
import com.example.tableorder.service.payment.PaymentTxService.WebhookOutcome;
import com.example.tableorder.support.JwtTokens;
import com.example.tableorder.support.MySqlTestcontainersConfig;
import com.example.tableorder.support.PaymentFixtures;
import com.example.tableorder.support.PaymentFixtures.TableRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.example.tableorder.support.PgWireMock.PG;
import static com.example.tableorder.support.PgWireMock.calls;
import static com.example.tableorder.support.PgWireMock.cancelReturns;
import static com.example.tableorder.support.PgWireMock.lookupReturns;
import static com.example.tableorder.support.PgWireMock.paymentJson;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 3단계: 웹훅(FR-7, T-5), 취소(FR-8, T-7), 복구 배치(FR-9), 주문 결제 상태(FR-10).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfig.class)
class PaymentWebhookCancelMySqlTest {

    static final String SECRET = "test-secret-that-is-definitely-longer-than-32-bytes";

    @DynamicPropertySource
    static void tossProps(DynamicPropertyRegistry r) {
        com.example.tableorder.support.PgWireMock.register(r);
    }

    @Autowired PaymentFacade facade;
    @Autowired PaymentRecoveryService recovery;
    @Autowired OrderDetailService orderDetailService;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    TableRef table;

    @BeforeEach
    void setUp() {
        PG.resetAll();
        table = PaymentFixtures.newTable(jdbc);
    }

    // ------------------------------------------------------------------ 헬퍼

    /** 결제를 원하는 상태로 만든다(DB 직접). */
    private PaymentResponse paymentIn(PaymentStatus status, String paymentKey) {
        long orderId = PaymentFixtures.newOrder(jdbc, table, 15000, "ORDERED");
        PaymentResponse p = facade.prepare(table.storeId(), table.tableId(), orderId).payment();
        jdbc.update("UPDATE payment SET status=?, payment_key=? WHERE id=?", status.name(), paymentKey, p.paymentId());
        return p;
    }

    private PaymentStatus dbStatus(Long id) {
        return PaymentStatus.valueOf(jdbc.queryForObject("SELECT status FROM payment WHERE id=?", String.class, id));
    }

    private long version(Long id) {
        return jdbc.queryForObject("SELECT version FROM payment WHERE id=?", Long.class, id);
    }

    private static TossWebhookRequest webhook(String paymentKey, String pgOrderId, String status) {
        return new TossWebhookRequest("PAYMENT_STATUS_CHANGED", "2026-11-10T12:00:01.000000",
                new TossWebhookRequest.Data(paymentKey, pgOrderId, status));
    }

    private static String webhookJson(String paymentKey, String pgOrderId, String status) {
        return """
                {"eventType":"PAYMENT_STATUS_CHANGED","createdAt":"2026-11-10T12:00:01.000000",
                 "data":%s}""".formatted(paymentJson(paymentKey, pgOrderId, status, 15000));
    }

    private String ownerToken(long storeId) {
        return "Bearer " + JwtTokens.owner(SECRET, "owner", storeId);
    }

    // ------------------------------------------------------------------ 웹훅 (FR-7)

    @Test
    @DisplayName("T-5 같은 웹훅이 3번 동시에 와도 상태 변경은 1번, 응답은 모두 정상")
    void duplicateWebhooksUpdateOnce() throws Exception {
        PaymentResponse p = paymentIn(PaymentStatus.IN_PROGRESS, "pk-t5");
        lookupReturns("pk-t5", p.pgOrderId(), "DONE", 15000);
        long before = version(p.paymentId());
        TossWebhookRequest event = webhook("pk-t5", p.pgOrderId(), "DONE");

        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<WebhookOutcome>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return facade.handleWebhook(event);
            }));
        }
        start.countDown();
        List<WebhookOutcome> outcomes = new ArrayList<>();
        for (Future<WebhookOutcome> f : futures) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn(o -> o == WebhookOutcome.UPDATED).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o == WebhookOutcome.DUPLICATE).hasSize(2);
        assertThat(dbStatus(p.paymentId())).isEqualTo(PaymentStatus.DONE);
        assertThat(version(p.paymentId())).isEqualTo(before + 1); // UPDATE 정확히 1회
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_event WHERE event_key=?",
                Integer.class, event.eventKey())).isEqualTo(1);

        // 같은 웹훅이 HTTP로 또 와도 200
        mvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content(webhookJson("pk-t5", p.pgOrderId(), "DONE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DUPLICATE"));
    }

    @Test
    @DisplayName("웹훅 본문이 아니라 PG 조회 결과를 믿는다 (위조 웹훅 방어)")
    void webhookBodyIsNotTrusted() {
        PaymentResponse p = paymentIn(PaymentStatus.IN_PROGRESS, "pk-forged");
        lookupReturns("pk-forged", p.pgOrderId(), "ABORTED", 15000); // 실제로는 승인되지 않음

        WebhookOutcome outcome = facade.handleWebhook(webhook("pk-forged", p.pgOrderId(), "DONE"));

        assertThat(outcome).isEqualTo(WebhookOutcome.UPDATED);
        assertThat(dbStatus(p.paymentId())).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("토스 상점관리자에서 취소하면 웹훅으로 CANCELED 반영")
    void dashboardCancelArrivesByWebhook() {
        PaymentResponse p = paymentIn(PaymentStatus.DONE, "pk-dash");
        lookupReturns("pk-dash", p.pgOrderId(), "CANCELED", 15000);

        facade.handleWebhook(webhook("pk-dash", p.pgOrderId(), "CANCELED"));

        assertThat(dbStatus(p.paymentId())).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    @DisplayName("PG 조회가 실패하면 503 → 토스 재전송 때 다시 처리 (이벤트를 먹어버리지 않음)")
    void webhookLookupFailureAsksForRedelivery() throws Exception {
        PaymentResponse p = paymentIn(PaymentStatus.IN_PROGRESS, "pk-retry");
        PG.stubFor(get(urlEqualTo("/v1/payments/orders/" + p.pgOrderId())).willReturn(aResponse().withStatus(500)));
        String body = webhookJson("pk-retry", p.pgOrderId(), "DONE");

        mvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isServiceUnavailable());
        assertThat(dbStatus(p.paymentId())).isEqualTo(PaymentStatus.IN_PROGRESS);

        lookupReturns("pk-retry", p.pgOrderId(), "DONE", 15000);
        mvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("UPDATED"));
        assertThat(dbStatus(p.paymentId())).isEqualTo(PaymentStatus.DONE);
    }

    // ------------------------------------------------------------------ 취소 (FR-8)

    @Test
    @DisplayName("T-7 취소를 두 번 요청해도 PG 취소는 1번, 결과는 CANCELED")
    void repeatedCancelRefundsOnce() {
        PaymentResponse p = paymentIn(PaymentStatus.DONE, "pk-t7");
        cancelReturns("pk-t7", 200, paymentJson("pk-t7", p.pgOrderId(), "CANCELED", 15000));

        assertThat(facade.cancel(p.paymentId(), "손님 요청").status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(facade.cancel(p.paymentId(), "손님 요청").status()).isEqualTo(PaymentStatus.CANCELED);

        assertThat(calls("POST", "/v1/payments/pk-t7/cancel")).isEqualTo(1);
        PG.verify(postRequestedFor(urlEqualTo("/v1/payments/pk-t7/cancel"))
                .withHeader("Idempotency-Key", equalTo("cancel-pk-t7")));
    }

    @Test
    @DisplayName("T-7b 동시 취소 2건은 같은 멱등키로 나가 PG가 한 번으로 취급, DB는 CANCELED")
    void concurrentCancelsShareIdempotencyKey() throws Exception {
        PaymentResponse p = paymentIn(PaymentStatus.DONE, "pk-t7b");
        cancelReturns("pk-t7b", 200, paymentJson("pk-t7b", p.pgOrderId(), "CANCELED", 15000));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return facade.cancel(p.paymentId(), "연타");
            }));
        }
        start.countDown();
        for (Future<PaymentResponse> f : futures) {
            assertThat(f.get().status()).isEqualTo(PaymentStatus.CANCELED);
        }
        pool.shutdown();

        int sent = calls("POST", "/v1/payments/pk-t7b/cancel");
        assertThat(sent).isBetween(1, 2);
        PG.verify(sent, postRequestedFor(urlEqualTo("/v1/payments/pk-t7b/cancel"))
                .withHeader("Idempotency-Key", equalTo("cancel-pk-t7b")));
        assertThat(dbStatus(p.paymentId())).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    @DisplayName("DONE이 아닌 결제는 취소할 수 없다 (409, PG 호출 없음)")
    void onlyDoneCanBeCanceled() {
        PaymentResponse p = paymentIn(PaymentStatus.IN_PROGRESS, "pk-nc");
        assertThatThrownBy(() -> facade.cancel(p.paymentId(), "x")).isInstanceOf(ConflictException.class);
        assertThat(PG.getAllServeEvents()).isEmpty();
    }

    @Test
    @DisplayName("PG가 '이미 취소됨'이라고 하면 기록만 CANCELED로 맞춘다")
    void alreadyCanceledAtPgIsReconciled() {
        PaymentResponse p = paymentIn(PaymentStatus.DONE, "pk-ac");
        cancelReturns("pk-ac", 400, "{\"code\":\"ALREADY_CANCELED_PAYMENT\",\"message\":\"이미 취소\"}");

        assertThat(facade.cancel(p.paymentId(), "x").status()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    @DisplayName("취소 API: 다른 매장 점주 403, 토큰 없음 401, PG 오류 503, 성공 200")
    void cancelEndpointAuthorization() throws Exception {
        PaymentResponse p = paymentIn(PaymentStatus.DONE, "pk-http");
        String url = "/api/admin/payments/" + p.paymentId() + "/cancel";
        String body = "{\"reason\":\"손님 요청\"}";

        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization", ownerToken(table.storeId() + 999_999))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        cancelReturns("pk-http", 500, "{\"code\":\"FAILED_INTERNAL_SYSTEM_PROCESSING\",\"message\":\"x\"}");
        mvc.perform(post(url).header("Authorization", ownerToken(table.storeId()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isServiceUnavailable());
        assertThat(dbStatus(p.paymentId())).isEqualTo(PaymentStatus.DONE);

        cancelReturns("pk-http", 200, paymentJson("pk-http", p.pgOrderId(), "CANCELED", 15000));
        mvc.perform(post(url).header("Authorization", ownerToken(table.storeId()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    // ------------------------------------------------------------------ 복구 배치 (FR-9)

    @Test
    @DisplayName("FR-9 오래 멈춘 결제만 PG 조회로 정리하고, 진행 중인 결제는 건드리지 않는다")
    void recoveryBatchResolvesOnlyStalePayments() {
        PaymentResponse staleDone = paymentIn(PaymentStatus.IN_PROGRESS, "pk-s1");
        PaymentResponse staleLost = paymentIn(PaymentStatus.UNKNOWN, "pk-s2");
        PaymentResponse fresh = paymentIn(PaymentStatus.IN_PROGRESS, "pk-s3");
        jdbc.update("UPDATE payment SET updated_at = NOW() - INTERVAL 10 MINUTE WHERE id IN (?, ?)",
                staleDone.paymentId(), staleLost.paymentId());
        lookupReturns("pk-s1", staleDone.pgOrderId(), "DONE", 15000);
        PG.stubFor(get(urlEqualTo("/v1/payments/orders/" + staleLost.pgOrderId())).willReturn(aResponse()
                .withStatus(404).withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"NOT_FOUND_PAYMENT\",\"message\":\"없음\"}")));

        recovery.recoverStale(Duration.ofMinutes(1));

        assertThat(dbStatus(staleDone.paymentId())).isEqualTo(PaymentStatus.DONE);
        assertThat(dbStatus(staleLost.paymentId())).isEqualTo(PaymentStatus.FAILED); // PG에 없음 + 5분 경과
        assertThat(dbStatus(fresh.paymentId())).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(calls("GET", "/v1/payments/orders/" + fresh.pgOrderId())).isZero();
        assertThat(calls("POST", "/v1/payments/confirm")).isZero(); // 복구는 승인을 다시 부르지 않는다
    }

    // ------------------------------------------------------------------ 주문 결제 상태 (FR-10)

    @Test
    @DisplayName("FR-10 주문 상세의 결제 상태가 실제 결제를 반영한다")
    void orderDetailReflectsPayment() {
        long orderId = PaymentFixtures.newOrder(jdbc, table, 15000, "ORDERED");
        OrderDetailDto unpaid = orderDetailService.getOrderDetail(orderId);
        assertThat(unpaid.getPaymentStatus()).isEqualTo(OrderPaymentStatus.UNPAID);

        PaymentResponse p = facade.prepare(table.storeId(), table.tableId(), orderId).payment();
        jdbc.update("UPDATE payment SET status='DONE', payment_key='pk-fr10' WHERE id=?", p.paymentId());
        assertThat(orderDetailService.getOrderDetail(orderId).getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);

        jdbc.update("UPDATE payment SET status='CANCELED' WHERE id=?", p.paymentId());
        assertThat(orderDetailService.getOrderDetail(orderId).getPaymentStatus()).isEqualTo(OrderPaymentStatus.REFUNDED);
    }
}
