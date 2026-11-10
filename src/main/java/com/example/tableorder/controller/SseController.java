package com.example.tableorder.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.tableorder.dto.realtime.ViewerCountEvent;
import com.example.tableorder.service.SseRealtimeStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE(Server-Sent Events) 컨트롤러 실시간 접속자 수를 클라이언트에게 스트리밍
 */
@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "실시간 통신", description = "SSE 기반 실시간 접속자 수 관리 API")
public class SseController {

    private final SseRealtimeStatsService sseRealtimeStatsService;

    /**
     * 실시간 접속자 수 SSE 스트림 연결
     *
     * @param storeId 매장 ID
     * @return SseEmitter - 실시간 이벤트 스트림
     */
    @GetMapping(value = "/viewer-count/{storeId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "실시간 접속자 수 스트림 연결", description = "SSE를 통해 실시간 접속자 수를 스트리밍합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "SSE 스트림 연결 성공",
                content = @Content(mediaType = "text/event-stream")),
        @ApiResponse(responseCode = "400", description = "잘못된 매장 ID"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<SseEmitter> streamViewerCount(
            @Parameter(description = "매장 ID", required = true, example = "1")
            @PathVariable Long storeId) {
        log.info("SSE 연결 요청 - 매장 ID: {}", storeId);

        SseEmitter emitter = sseRealtimeStatsService.createViewerCountStream(storeId);

        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .body(emitter);
    }

    /**
     * 현재 접속자 수 조회 (REST API)
     *
     * @param storeId 매장 ID
     * @return 현재 접속자 수
     */
    @GetMapping("/viewer-count/{storeId}/current")
    @Operation(summary = "현재 접속자 수 조회", description = "특정 매장의 현재 접속자 수를 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ViewerCountEvent.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 매장 ID"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<ViewerCountEvent> getCurrentViewerCount(
            @Parameter(description = "매장 ID", required = true, example = "1")
            @PathVariable Long storeId) {
        log.info("현재 접속자 수 조회 - 매장 ID: {}", storeId);

        ViewerCountEvent event = sseRealtimeStatsService.getCurrentViewerCount(storeId);
        return ResponseEntity.ok(event);
    }

    /**
     * 접속자 수 리셋 (테스트용)
     *
     * @param storeId 매장 ID
     * @return 리셋된 접속자 수 이벤트
     */
    @PreAuthorize("hasRole('OWNER') and @storeAccess.owns(#storeId)")
    @PostMapping("/viewer-count/{storeId}/reset")
    @Operation(summary = "접속자 수 리셋", description = "특정 매장의 접속자 수를 0으로 리셋합니다 (테스트용)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "리셋 성공",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = ViewerCountEvent.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 매장 ID"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<ViewerCountEvent> resetViewerCount(
            @Parameter(description = "매장 ID", required = true, example = "1")
            @PathVariable Long storeId) {
        log.info("접속자 수 리셋 - 매장 ID: {}", storeId);

        ViewerCountEvent event = sseRealtimeStatsService.resetViewerCount(storeId);
        return ResponseEntity.ok(event);
    }

    /**
     * 연결 해제 처리 (탭 닫기 시 호출)
     *
     * @param request 연결 해제 요청
     * @return 성공 응답
     */
    @PostMapping("/disconnect")
    @Operation(summary = "연결 해제", description = "클라이언트 연결 해제 처리 (탭 닫기 시 호출)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "연결 해제 처리 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<String> disconnect(@RequestBody String request) {
        try {
            // JSON 파싱 (간단한 처리)
            if (request.contains("disconnect")) {
                log.info("클라이언트 연결 해제 요청 수신");
                // 실제 연결 해제는 SSE의 onCompletion 콜백에서 처리됨
                return ResponseEntity.ok("연결 해제 처리됨");
            }
            return ResponseEntity.badRequest().body("잘못된 요청");
        } catch (Exception e) {
            log.error("연결 해제 처리 중 오류: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("서버 오류");
        }
    }

}
