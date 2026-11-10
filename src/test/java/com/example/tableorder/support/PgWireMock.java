package com.example.tableorder.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/** 토스페이먼츠 대역(WireMock). JVM 전체에서 하나만 띄운다. */
public final class PgWireMock {

    public static final WireMockServer PG = start();
    public static final String CONFIRM = "/v1/payments/confirm";

    private PgWireMock() {
    }

    /** 로컬 루프백에만 바인딩. 포트 바인딩이 일시적으로 실패하는 환경을 위해 몇 번 재시도한다. */
    private static WireMockServer start() {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            WireMockServer server = new WireMockServer(
                    wireMockConfig().bindAddress("127.0.0.1").dynamicPort().containerThreads(150));
            try {
                server.start();
                return server;
            } catch (RuntimeException e) {
                last = e;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw last;
    }

    /** 테스트를 빠르게 하려고 응답 타임아웃을 1초로 둔다 (운영 기본 3초). */
    public static void register(DynamicPropertyRegistry r) {
        r.add("toss.base-url", PG::baseUrl);
        r.add("toss.secret-key", () -> "test_sk_dummy");
        r.add("toss.client-key", () -> "test_ck_dummy");
        r.add("toss.read-timeout", () -> "PT1S");
    }

    public static String paymentJson(String paymentKey, String pgOrderId, String status, int amount) {
        return """
                {"paymentKey":"%s","orderId":"%s","status":"%s","method":"카드",
                 "totalAmount":%d,"approvedAt":"2026-11-10T12:00:00+09:00"}""".formatted(paymentKey, pgOrderId, status, amount);
    }

    public static void lookupReturns(String paymentKey, String pgOrderId, String status, int amount) {
        PG.stubFor(get(urlEqualTo("/v1/payments/orders/" + pgOrderId)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(paymentJson(paymentKey, pgOrderId, status, amount))));
    }

    public static void cancelReturns(String paymentKey, int httpStatus, String body) {
        PG.stubFor(post(urlEqualTo("/v1/payments/" + paymentKey + "/cancel")).willReturn(aResponse()
                .withStatus(httpStatus).withHeader("Content-Type", "application/json").withBody(body)));
    }

    public static int calls(String method, String url) {
        var pattern = "POST".equals(method) ? postRequestedFor(urlEqualTo(url))
                : com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo(url));
        return PG.countRequestsMatching(pattern.build()).getCount();
    }
}
