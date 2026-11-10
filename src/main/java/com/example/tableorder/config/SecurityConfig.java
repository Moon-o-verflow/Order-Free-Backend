package com.example.tableorder.config;

import com.example.tableorder.util.JwtDecoderUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Spring Security 설정.
 *
 * <ul>
 *   <li>고객용 API(메뉴·장바구니·주문·직원호출·접속자 SSE)는 토큰 없이 허용한다.</li>
 *   <li>그 밖의 /api/** 는 ROLE_OWNER 가 필요하다.</li>
 *   <li>매장 소유권은 컨트롤러의 {@code @PreAuthorize("@storeAccess...")} 로 검사한다.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_DOCS = {
            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**"
    };

    private static final String[] PUBLIC_STATIC = {
            "/favicon.ico", "/static/**", "/*.html", "/*.js", "/*.css", "/error"
    };

    /** 고객(테이블 QR 접속) 경로. 경로 아래의 점주 전용 기능은 메서드 보안으로 막는다. */
    private static final String[] PUBLIC_CUSTOMER = {
            "/api/stores/*/tables/*/menus/**",
            "/api/stores/*/tables/*/carts/**",
            "/api/stores/*/tables/*/orders/**",
            "/api/customer/**",
    };

    private final JwtDecoderUtil jwtDecoderUtil;

    public SecurityConfig(JwtDecoderUtil jwtDecoderUtil) {
        this.jwtDecoderUtil = jwtDecoderUtil;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_DOCS).permitAll()
                        .requestMatchers(PUBLIC_STATIC).permitAll()
                        .requestMatchers(PUBLIC_CUSTOMER).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/stores").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sse/viewer-count/*", "/api/sse/viewer-count/*/current").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sse/disconnect").permitAll()
                        .requestMatchers("/api/**").hasRole("OWNER")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(req, res, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(req, res, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."))
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtDecoderUtil), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** GlobalExceptionHandler 와 같은 형식의 에러 본문. */
    private static void writeError(HttpServletRequest req, HttpServletResponse res,
                                   int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write(String.format(
                "{\"timestamp\":\"%s\",\"path\":\"%s\",\"error\":\"%s\",\"message\":\"%s\",\"status\":%d}",
                ZonedDateTime.now(ZoneId.of("Asia/Seoul")), req.getRequestURI().replace("\"", ""),
                error, message, status));
    }
}
