package com.example.tableorder.util;

import com.example.tableorder.config.OwnerPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.crypto.SecretKey;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JWT 토큰 검증 유틸.
 * - HS256 서명, issuer, 만료(parser가 검증), role=OWNER 확인.
 * - storeIds 클레임을 읽어 {@link OwnerPrincipal}을 만든다.
 */
public class JwtDecoderUtil {

    public static final String ROLE_CLAIM = "role";
    public static final String STORE_IDS_CLAIM = "storeIds";

    private final SecretKey secretKey;
    private final String issuer;

    public JwtDecoderUtil(SecretKey secretKey, String issuer) {
        this.secretKey = secretKey;
        this.issuer = issuer;
    }

    /**
     * @param token Bearer 접두사를 뗀 JWT
     * @throws JwtException 서명·issuer·만료·역할 검증 실패 시
     */
    public Authentication validateAndGetAuthentication(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!"OWNER".equals(claims.get(ROLE_CLAIM, String.class))) {
            throw new JwtException("OWNER 역할이 아닙니다.");
        }

        OwnerPrincipal principal = new OwnerPrincipal(claims.getSubject(), readStoreIds(claims));
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    }

    private static Set<Long> readStoreIds(Claims claims) {
        Object raw = claims.get(STORE_IDS_CLAIM);
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        for (Object item : list) {
            if (item instanceof Number n) {
                ids.add(n.longValue());
            } else {
                throw new JwtException("storeIds 클레임 형식이 올바르지 않습니다.");
            }
        }
        return ids;
    }
}
