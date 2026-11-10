package com.example.tableorder.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 전역 예외 핸들러.
 * - 에러 응답 형식: {timestamp, path, error, message, status}
 * - 예상하지 못한 예외는 로그에 남기고, 응답에는 내부 메시지를 노출하지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex, WebRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex, WebRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRule(BusinessRuleException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BUSINESS_RULE_VIOLATION", ex.getMessage(), request);
    }

    @ExceptionHandler({BadRequestException.class, ValidationException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex, WebRequest request) {
        String code = ex instanceof ValidationException ? "VALIDATION_ERROR" : "BAD_REQUEST";
        return build(HttpStatus.BAD_REQUEST, code, ex.getMessage(), request);
    }

    @ExceptionHandler(InternalErrorException.class)
    public ResponseEntity<Map<String, Object>> handleInternalError(InternalErrorException ex, WebRequest request) {
        log.error("내부 오류: {}", request.getDescription(false), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(com.example.tableorder.service.payment.PgUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handlePgUnavailable(RuntimeException ex, WebRequest request) {
        log.warn("PG 연동 일시 실패: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "PG_UNAVAILABLE", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthorizationDenied(AuthorizationDeniedException ex, WebRequest request) {
        // @PreAuthorize 실패: 토큰이 없으면 401, 있지만 권한(매장 소유권)이 없으면 403
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.", request);
        }
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(error -> (error instanceof FieldError fe ? fe.getField() : error.getObjectName())
                        + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "요청 본문을 읽을 수 없습니다.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "잘못된 파라미터 값: " + ex.getName(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex, WebRequest request) {
        // 404·405·누락된 파라미터 등 Spring MVC 표준 예외는 원래 상태 코드를 유지한다
        if (ex instanceof ErrorResponse er) {
            HttpStatusCode status = er.getStatusCode();
            String reason = HttpStatus.resolve(status.value()) != null
                    ? HttpStatus.valueOf(status.value()).name() : "ERROR";
            String detail = er.getBody().getDetail();
            return build(status, reason, detail != null ? detail : reason, request);
        }
        log.error("처리되지 않은 예외: {}", request.getDescription(false), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다.", request);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatusCode status, String errorCode, String message, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", ZonedDateTime.now(SEOUL).toString());
        body.put("path", request.getDescription(false).replace("uri=", ""));
        body.put("error", errorCode);
        body.put("message", message);
        body.put("status", status.value());
        return new ResponseEntity<>(body, status);
    }
}
