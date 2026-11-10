package com.example.tableorder.service.payment;

import com.example.tableorder.dto.payment.PaymentConfirmRequest;
import com.example.tableorder.dto.payment.PaymentResponse;
import com.example.tableorder.entity.payment.Payment;
import com.example.tableorder.entity.payment.PaymentAmountMismatchException;
import com.example.tableorder.entity.payment.PaymentStatus;
import com.example.tableorder.exception.ConflictException;
import com.example.tableorder.repository.PaymentRepository;
import com.example.tableorder.service.payment.pg.PgClient;
import com.example.tableorder.service.payment.pg.PgPayment;
import com.example.tableorder.support.MySqlTestcontainersConfig;
import com.example.tableorder.support.PaymentFixtures;
import com.example.tableorder.support.PaymentFixtures.TableRef;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제 승인 동시성·장애 테스트 (PRD T-1 ~ T-4, T-6, T-8).
 * 실제 MySQL(행 락·UNIQUE)과 WireMock(토스페이먼츠 대역)으로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfig.class)
class PaymentApprovalMySqlTest {

    static final WireMockServer PG = com.example.tableorder.support.PgWireMock.PG;

    @AfterAll
    static void stopPg() {
        PG.resetAll();
    }

    @DynamicPropertySource
    static void tossProps(DynamicPropertyRegistry r) {
        com.example.tableorder.support.PgWireMock.register(r);
    }

    static final String CONFIRM = "/v1/payments/confirm";

    @Autowired PaymentFacade facade;
    @Autowired PaymentRecoveryService recovery;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PgClient pgClient;
    @Autowired TransactionTemplate txTemplate;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @SpyBean PaymentTxService txService;

    TableRef table;

    @BeforeEach
    void setUp() {
        PG.resetAll();
        table = PaymentFixtures.newTable(jdbc);
    }

    // ------------------------------------------------------------------ 헬퍼

    private PaymentResponse prepared(int amount) {
        long orderId = PaymentFixtures.newOrder(jdbc, table, amount, "ORDERED");
        return facade.prepare(table.storeId(), table.tableId(), orderId).payment();
    }

    private static String approvedJson(String paymentKey, String pgOrderId, int amount) {
        return """
                {"paymentKey":"%s","orderId":"%s","status":"DONE","method":"카드",
                 "totalAmount":%d,"approvedAt":"2026-11-10T12:00:00+09:00"}""".formatted(paymentKey, pgOrderId, amount);
    }

    private static void pgApproves(String paymentKey, String pgOrderId, int amount, int delayMs) {
        PG.stubFor(post(CONFIRM).willReturn(aResponse().withStatus(200).withFixedDelay(delayMs)
                .withHeader("Content-Type", "application/json").withBody(approvedJson(paymentKey, pgOrderId, amount))));
    }

    private static void pgLookupReturnsDone(String paymentKey, String pgOrderId, int amount) {
        PG.stubFor(get(urlEqualTo("/v1/payments/orders/" + pgOrderId)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(approvedJson(paymentKey, pgOrderId, amount))));
    }

    private static int confirmCalls() {
        return PG.countRequestsMatching(postRequestedFor(urlEqualTo(CONFIRM)).build()).getCount();
    }

    private PaymentStatus dbStatus(Long paymentId) {
        return PaymentStatus.valueOf(jdbc.queryForObject("SELECT status FROM payment WHERE id=?", String.class, paymentId));
    }

    // ------------------------------------------------------------------ 테스트

    @Test
    @DisplayName("T-1 같은 승인 요청 100건이 동시에 와도 PG 승인 호출은 정확히 1번")
    void hundredConcurrentConfirmsCallPgOnce() throws Exception {
        PaymentResponse p = prepared(15000);
        pgApproves("pk-t1", p.pgOrderId(), 15000, 300); // 승인 중 경합 구간을 넓힌다
        PaymentConfirmRequest req = new PaymentConfirmRequest("pk-t1", p.pgOrderId(), 15000);

        int n = 100;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentResponse>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return facade.confirm(req);
            }));
        }
        start.countDown();

        int done = 0, conflict = 0;
        for (Future<PaymentResponse> f : futures) {
            try {
                assertThat(f.get().status()).isEqualTo(PaymentStatus.DONE);
                done++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(ConflictException.class);
                conflict++;
            }
        }
        pool.shutdown();

        assertThat(confirmCalls()).isEqualTo(1);
        assertThat(done + conflict).isEqualTo(n);
        assertThat(done).isGreaterThanOrEqualTo(1); // 승자 1 + 승인 뒤 도착해 저장된 결과를 받은 요청
        assertThat(dbStatus(p.paymentId())).isEqualTo(PaymentStatus.DONE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE payment_key='pk-t1'", Integer.class)).isEqualTo(1);
        System.out.printf("[T-1] 동시 요청 %d건 → PG 승인 호출 %d건, DONE 응답 %d건, 409 %d건%n",
                n, confirmCalls(), done, conflict);
    }

    @Test
    @DisplayName("T-2 금액을 1원 바꾸면 PG 호출 없이 400")
    void tamperedAmountIsRejectedWithoutPgCall() {
        PaymentResponse p = prepared(15000);
        pgApproves("pk-t2", p.pgOrderId(), 15001, 0);

        assertThatThrownBy(() -> facade.confirm(new PaymentConfirmRequest("pk-t2", p.pgOrderId(), 15001)))
                .isInstanceOf(PaymentAmountMismatchException.class);
        assertThat(confirmCalls()).isZero();
        assertThat(dbStatus(p.paymentId())).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("T-3 PG 응답이 타임아웃되면 재승인 없이 조회로 DONE 확정")
    void timeoutIsResolvedByLookupNotRetry() {
        PaymentResponse p = prepared(15000);
        pgApproves("pk-t3", p.pgOrderId(), 15000, 3000); // 응답 타임아웃(1초)보다 느림
        pgLookupReturnsDone("pk-t3", p.pgOrderId(), 15000);

        PaymentResponse result = facade.confirm(new PaymentConfirmRequest("pk-t3", p.pgOrderId(), 15000));

        assertThat(result.status()).isEqualTo(PaymentStatus.DONE);
        assertThat(confirmCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("T-3b 타임아웃 뒤 PG 기록이 아직 없으면 UNKNOWN으로 두고, 클라이언트 재요청도 재승인하지 않음")
    void unknownStaysUntilLookupSucceeds() {
        PaymentResponse p = prepared(15000);
        pgApproves("pk-t3b", p.pgOrderId(), 15000, 3000);
        PG.stubFor(get(urlEqualTo("/v1/payments/orders/" + p.pgOrderId())).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"NOT_FOUND_PAYMENT\",\"message\":\"없음\"}")));
        PaymentConfirmRequest req = new PaymentConfirmRequest("pk-t3b", p.pgOrderId(), 15000);

        assertThat(facade.confirm(req).status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThatThrownBy(() -> facade.confirm(req)).isInstanceOf(ConflictException.class);
        assertThat(confirmCalls()).isEqualTo(1);

        // 나중에 PG 조회가 DONE을 돌려주면 복구로 확정
        pgLookupReturnsDone("pk-t3b", p.pgOrderId(), 15000);
        assertThat(recovery.recover(p.paymentId()).status()).isEqualTo(PaymentStatus.DONE);
        assertThat(confirmCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("T-4 PG 승인 후 TX2가 실패해도 기록이 유실되지 않고 복구로 DONE 확정")
    void dbFailureAfterPgApprovalIsRecovered() {
        PaymentResponse p = prepared(15000);
        pgApproves("pk-t4", p.pgOrderId(), 15000, 0);
        doThrow(new IllegalStateException("TX2에서 강제 예외"))
                .doCallRealMethod()
                .when(txService).markDone(anyLong(), any(PgPayment.class));

        PaymentResponse result = facade.confirm(new PaymentConfirmRequest("pk-t4", p.pgOrderId(), 15000));

        assertThat(result.status()).isEqualTo(PaymentStatus.IN_PROGRESS); // 승인 시도 기록이 남아 있다
        assertThat(dbStatus(p.paymentId())).isEqualTo(PaymentStatus.IN_PROGRESS);

        pgLookupReturnsDone("pk-t4", p.pgOrderId(), 15000);
        assertThat(recovery.recover(p.paymentId()).status()).isEqualTo(PaymentStatus.DONE);
        assertThat(confirmCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("T-6 이미 DONE인 결제에 같은 승인 요청이 오면 저장된 결과를 반환, PG 재호출 없음")
    void repeatedConfirmReturnsStoredResult() {
        PaymentResponse p = prepared(15000);
        pgApproves("pk-t6", p.pgOrderId(), 15000, 0);
        PaymentConfirmRequest req = new PaymentConfirmRequest("pk-t6", p.pgOrderId(), 15000);

        PaymentResponse first = facade.confirm(req);
        PaymentResponse again = facade.confirm(req);

        assertThat(again).isEqualTo(first);
        assertThat(confirmCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("PG 거절 시 FAILED로 기록하고, 다시 준비하면 새 주문번호로 재시도 가능")
    void declinedPaymentIsFailedAndRetryable() {
        PaymentResponse p = prepared(15000);
        PG.stubFor(post(CONFIRM).willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                .withBody("{\"code\":\"REJECT_CARD_COMPANY\",\"message\":\"카드사 거절\"}")));

        assertThatThrownBy(() -> facade.confirm(new PaymentConfirmRequest("pk-d", p.pgOrderId(), 15000)))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("REJECT_CARD_COMPANY");
        assertThat(dbStatus(p.paymentId())).isEqualTo(PaymentStatus.FAILED);

        PaymentResponse retried = facade.prepare(table.storeId(), table.tableId(), p.orderId()).payment();
        assertThat(retried.status()).isEqualTo(PaymentStatus.READY);
        assertThat(retried.pgOrderId()).isNotEqualTo(p.pgOrderId());
    }

    @Test
    @DisplayName("PG 승인 요청에 결제마다 고정된 Idempotency-Key를 붙인다")
    void confirmSendsIdempotencyKey() {
        PaymentResponse p = prepared(15000);
        pgApproves("pk-idem", p.pgOrderId(), 15000, 0);

        facade.confirm(new PaymentConfirmRequest("pk-idem", p.pgOrderId(), 15000));

        PG.verify(postRequestedFor(urlEqualTo(CONFIRM))
                .withHeader("Idempotency-Key", equalTo("confirm-" + p.pgOrderId()))
                .withHeader("Authorization", equalTo("Basic dGVzdF9za19kdW1teTo=")) // base64("test_sk_dummy:")
                .withRequestBody(matchingJsonPath("$.amount", equalTo("15000"))));
    }

    @Test
    @DisplayName("T-8 (역방향 실험) PG 호출을 한 트랜잭션 안에 두면 'PG 승인됨 + DB 미결제'가 된다")
    void naiveSingleTransactionLosesApproval() {
        PaymentResponse p = prepared(15000);
        pgApproves("pk-t8", p.pgOrderId(), 15000, 0);

        // 잘못된 버전: 조회 → PG 승인 → 상태 변경을 하나의 @Transactional 안에서 하고, 커밋 전에 DB 예외
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            Payment payment = paymentRepository.findById(p.paymentId()).orElseThrow();
            PgPayment approved = pgClient.confirm("pk-t8", p.pgOrderId(), 15000);
            // 상태 머신 없이 곧장 DONE 으로 쓰는 단순한 구현을 흉내 낸다
            ReflectionTestUtils.setField(payment, "status", PaymentStatus.DONE);
            ReflectionTestUtils.setField(payment, "paymentKey", approved.paymentKey());
            throw new IllegalStateException("커밋 전 DB 예외");
        })).isInstanceOf(IllegalStateException.class);

        // PG는 승인했지만(호출 1회) DB는 롤백되어 아무 흔적이 없다 → 손님이 다시 결제하면 이중 결제
        assertThat(confirmCalls()).isEqualTo(1);
        assertThat(dbStatus(p.paymentId())).isEqualTo(PaymentStatus.READY);
        assertThat(jdbc.queryForObject("SELECT payment_key FROM payment WHERE id=?", String.class, p.paymentId())).isNull();
        // 같은 상황에서 올바른 버전은 IN_PROGRESS 로 남아 복구된다 → T-4
    }

    @Test
    @DisplayName("승인 API의 HTTP 상태: 200 DONE, 400 금액 불일치, 409 처리 중")
    void confirmEndpointStatusCodes() throws Exception {
        PaymentResponse p = prepared(15000);
        pgApproves("pk-http", p.pgOrderId(), 15000, 0);
        String url = "/api/payments/confirm";
        String body = "{\"paymentKey\":\"pk-http\",\"orderId\":\"%s\",\"amount\":%d}";

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(p.pgOrderId(), 1)))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(p.pgOrderId(), 15000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.method").value("카드"));

        jdbc.update("UPDATE payment SET status='IN_PROGRESS', payment_key=NULL WHERE id=?", p.paymentId());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(p.pgOrderId(), 15000)))
                .andExpect(status().isConflict());
    }
}
