package com.example.tableorder.service.payment.pg;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * 토스페이먼츠 결제 API 클라이언트.
 * <ul>
 *   <li>승인: POST /v1/payments/confirm — {@code Idempotency-Key}를 PG 주문번호에서 고정으로 만들어,
 *       같은 결제의 승인 요청이 혹시 두 번 나가도 PG가 첫 응답을 돌려주게 한다(PG 측 방어선).</li>
 *   <li>조회: GET /v1/payments/orders/{orderId} — 타임아웃 뒤 결과 확정용.</li>
 * </ul>
 */
@Slf4j
@Component
public class TossPaymentsClient implements PgClient {

    static final String ALREADY_PROCESSED = "ALREADY_PROCESSED_PAYMENT";
    static final String NOT_FOUND = "NOT_FOUND_PAYMENT";

    private final RestClient restClient;
    private final TossProperties props;

    public TossPaymentsClient(TossProperties props, RestClient.Builder builder) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        this.restClient = builder
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossError(String code, String message) {
    }

    @Override
    public PgPayment confirm(String paymentKey, String pgOrderId, int amount) {
        try {
            return restClient.post()
                    .uri("/v1/payments/confirm")
                    .header("Authorization", authorization())
                    .header("Idempotency-Key", "confirm-" + pgOrderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("paymentKey", paymentKey, "orderId", pgOrderId, "amount", amount))
                    .retrieve()
                    .body(PgPayment.class);
        } catch (RestClientResponseException e) {
            TossError err = parseError(e);
            if (e.getStatusCode().is4xxClientError() && !ALREADY_PROCESSED.equals(err.code())) {
                throw new PgException.Declined(err.code(), err.message());
            }
            // 5xx 또는 "이미 처리된 결제": 승인됐을 수도 있다
            throw new PgException.ResultUnknown(err.code(), err.message(), e);
        } catch (RestClientException e) {
            // 타임아웃·연결 실패·응답 읽기 실패: 요청이 PG에 닿았는지 알 수 없다
            throw new PgException.ResultUnknown("TIMEOUT", e.getMessage(), e);
        }
    }

    @Override
    public Optional<PgPayment> findByOrderId(String pgOrderId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/v1/payments/orders/{orderId}", pgOrderId)
                    .header("Authorization", authorization())
                    .retrieve()
                    .body(PgPayment.class));
        } catch (RestClientResponseException e) {
            TossError err = parseError(e);
            if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(404)) || NOT_FOUND.equals(err.code())) {
                return Optional.empty();
            }
            throw new PgException.ResultUnknown(err.code(), err.message(), e);
        } catch (RestClientException e) {
            throw new PgException.ResultUnknown("TIMEOUT", e.getMessage(), e);
        }
    }

    @Override
    public PgPayment cancel(String paymentKey, String reason) {
        try {
            return restClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .header("Authorization", authorization())
                    // 전액 취소는 결제당 한 번뿐이므로 결제 키로 고정. 토스는 같은 키에 첫 응답을 돌려준다
                    .header("Idempotency-Key", "cancel-" + paymentKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cancelReason", reason))
                    .retrieve()
                    .body(PgPayment.class);
        } catch (RestClientResponseException e) {
            TossError err = parseError(e);
            if (e.getStatusCode().is4xxClientError()) {
                throw new PgException.Declined(err.code(), err.message());
            }
            throw new PgException.ResultUnknown(err.code(), err.message(), e);
        } catch (RestClientException e) {
            throw new PgException.ResultUnknown("TIMEOUT", e.getMessage(), e);
        }
    }

    private String authorization() {
        if (props.secretKey() == null || props.secretKey().isBlank()) {
            throw new IllegalStateException("TOSS_SECRET_KEY가 설정되지 않았습니다.");
        }
        String raw = props.secretKey() + ":";
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static TossError parseError(RestClientResponseException e) {
        try {
            TossError err = e.getResponseBodyAs(TossError.class);
            if (err != null && err.code() != null) {
                return err;
            }
        } catch (RuntimeException ignored) {
            // 본문이 JSON이 아니면 상태 코드만 쓴다
        }
        return new TossError("HTTP_" + e.getStatusCode().value(), e.getStatusText());
    }
}
