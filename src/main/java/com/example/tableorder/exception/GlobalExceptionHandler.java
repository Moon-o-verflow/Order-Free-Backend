package com.example.tableorder.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 핸들러 클래스.
 * - 커스텀 예외 및 검증 오류를 고정 형식으로 응답.
 * - 에러 응답 형식: {timestamp, path, error, message, status}
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex, WebRequest request) {
        return buildErrorResponse(ex, "NOT_FOUND", HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex, WebRequest request) {
        return buildErrorResponse(ex, "CONFLICT", HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex, WebRequest request) {
        return buildErrorResponse(ex, "FORBIDDEN", HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex, WebRequest request) {
        return buildErrorResponse(ex, "UNAUTHORIZED", HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRule(BusinessRuleException ex, WebRequest request) {
        return buildErrorResponse(ex, "BUSINESS_RULE_VIOLATION", HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(InternalErrorException.class)
    public ResponseEntity<Map<String, Object>> handleInternalError(InternalErrorException ex, WebRequest request) {
        return buildErrorResponse(ex, "INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ValidationException ex, WebRequest request) {
        return buildErrorResponse(ex, "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthorizationDenied(AuthorizationDeniedException ex, WebRequest request) {
        // @PreAuthorize 실패: 토큰이 없으면 401, 있지만 권한(매장 소유권)이 없으면 403
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return buildErrorResponse(new Exception("인증이 필요합니다."), "UNAUTHORIZED", HttpStatus.UNAUTHORIZED, request);
        }
        return buildErrorResponse(new Exception("접근 권한이 없습니다."), "FORBIDDEN", HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        String message = errors.toString();
        return buildErrorResponse(new Exception(message), "VALIDATION_ERROR", HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(Exception ex, WebRequest request) {
        return buildErrorResponse(ex, "INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(Exception ex, String errorCode, HttpStatus status, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", ZonedDateTime.now(SEOUL).toString());
        body.put("path", request.getDescription(false).replace("uri=", ""));
        body.put("error", errorCode);
        body.put("message", ex.getMessage());
        body.put("status", status.value());
        return new ResponseEntity<>(body, status);
    }
}