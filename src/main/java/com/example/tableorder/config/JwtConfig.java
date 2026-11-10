package com.example.tableorder.config;

import com.example.tableorder.util.JwtDecoderUtil;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 설정. 시크릿(JWT_SECRET)이 없거나 32바이트보다 짧으면 기동을 막는다.
 */
@Configuration
public class JwtConfig {

    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    public SecretKey jwtSecretKey(@Value("${spring.security.jwt.secret:}") String jwtSecret) {
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET이 설정되지 않았거나 " + MIN_SECRET_BYTES + "바이트보다 짧습니다.");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    @Bean
    public JwtDecoderUtil jwtDecoderUtil(SecretKey jwtSecretKey,
                                         @Value("${spring.security.jwt.issuer}") String issuer) {
        return new JwtDecoderUtil(jwtSecretKey, issuer);
    }
}
