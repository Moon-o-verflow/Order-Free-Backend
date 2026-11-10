package com.example.tableorder.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 점주 JWT 발급 도우미 (테스트·로컬 개발용).
 *
 * 로컬에서 토큰이 필요하면 JWT_SECRET 환경변수를 설정하고 main을 실행한다.
 * 인자: ownerId storeId [storeId...]
 */
public final class JwtTokens {

    public static final String ISSUER = "tableorder-app";

    private JwtTokens() {
    }

    public static String owner(String secret, String ownerId, Long... storeIds) {
        return build(secret, ISSUER, ownerId, "OWNER", List.of(storeIds), 3_600_000L);
    }

    public static String build(String secret, String issuer, String subject, String role,
                               List<Long> storeIds, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .claim("role", role)
                .claim("storeIds", storeIds)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    public static void main(String[] args) {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || args.length < 2) {
            System.err.println("usage: JWT_SECRET=... JwtTokens <ownerId> <storeId> [storeId...]");
            System.exit(1);
        }
        Long[] ids = Arrays.stream(args).skip(1).map(Long::valueOf).toArray(Long[]::new);
        System.out.println("Bearer " + owner(secret, args[0], ids));
    }
}
