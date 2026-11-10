package com.example.tableorder.service.payment.pg;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 토스페이먼츠 설정. 키는 환경변수(TOSS_SECRET_KEY, TOSS_CLIENT_KEY)로만 주입한다.
 *
 * @param baseUrl        API 주소 (테스트에서는 WireMock 주소)
 * @param secretKey      서버용 시크릿 키 (test_sk_...)
 * @param clientKey      결제창용 클라이언트 키 (test_ck_...). 공개돼도 되는 값
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    응답 타임아웃. 넘으면 결과 미확정(UNKNOWN)으로 보고 조회로 확정한다
 */
@ConfigurationProperties(prefix = "toss")
public record TossProperties(
        String baseUrl,
        String secretKey,
        String clientKey,
        Duration connectTimeout,
        Duration readTimeout
) {
    public TossProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.tosspayments.com";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(2);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(3);
    }
}
