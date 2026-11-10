package com.example.tableorder.service;

import com.example.tableorder.dto.realtime.ViewerCountEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 기반 실시간 접속자 수 서비스. 메뉴판을 보고 있는 사용자 수를 매장별로 관리한다.
 *
 * <ul>
 *   <li>접속자 수는 별도 카운터 없이 열린 연결 수로 계산한다. 카운터와 연결 목록이 어긋날 일이 없다.</li>
 *   <li>하트비트는 연결마다 작업을 만들지 않고, 스케줄러 하나가 모든 연결에 주기적으로 보낸다.
 *       전송에 실패한 연결은 그때 정리한다.</li>
 * </ul>
 */
@Service
@Slf4j
public class SseRealtimeStatsService {

    /** 연결 유지 시간. 끝나면 브라우저 EventSource 가 자동으로 다시 연결한다. */
    static final long EMITTER_TIMEOUT_MS = 10 * 60 * 1000L;
    static final long HEARTBEAT_INTERVAL_MS = 15_000L;

    /** 매장 ID → 열린 연결 */
    private final Map<Long, Set<SseEmitter>> storeConnections = new ConcurrentHashMap<>();

    public SseEmitter createViewerCountStream(Long storeId) {
        SseEmitter emitter = createEmitter();
        // compute 는 키 단위로 원자적이라, 하트비트의 빈 항목 정리와 겹쳐도 연결이 유실되지 않는다
        storeConnections.compute(storeId, (id, set) -> {
            Set<SseEmitter> s = set != null ? set : ConcurrentHashMap.newKeySet();
            s.add(emitter);
            return s;
        });

        emitter.onCompletion(() -> disconnect(storeId, emitter));
        emitter.onTimeout(() -> disconnect(storeId, emitter));
        emitter.onError(e -> disconnect(storeId, emitter));

        log.info("SSE 연결 - 매장: {}, 접속자 수: {}", storeId, viewerCount(storeId));
        // 새 연결 포함 모두에게 갱신된 접속자 수 전송
        broadcastViewerCount(storeId);
        return emitter;
    }

    /** 테스트에서 전송 실패를 흉내 내기 위해 분리했다. */
    SseEmitter createEmitter() {
        return new SseEmitter(EMITTER_TIMEOUT_MS);
    }

    /** 연결 종료 처리. 여러 콜백에서 불려도 한 번만 반영된다. */
    void disconnect(Long storeId, SseEmitter emitter) {
        Set<SseEmitter> connections = storeConnections.get(storeId);
        if (connections == null || !connections.remove(emitter)) {
            return;
        }
        log.info("SSE 연결 종료 - 매장: {}, 접속자 수: {}", storeId, connections.size());
        broadcastViewerCount(storeId);
    }

    /** 매장의 모든 연결에 현재 접속자 수를 보낸다. */
    public void broadcastViewerCount(Long storeId) {
        Set<SseEmitter> connections = storeConnections.get(storeId);
        if (connections == null) {
            return;
        }
        ViewerCountEvent event = viewerCountEvent(storeId, connections.size(), null);
        for (SseEmitter emitter : connections) {
            sendOrDrop(storeId, emitter, SseEmitter.event().name("viewer-count").data(event));
        }
    }

    /** 끊어진 연결을 찾아내기 위한 주기적 하트비트. */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS, initialDelay = HEARTBEAT_INTERVAL_MS)
    public void sendHeartbeats() {
        storeConnections.forEach((storeId, connections) -> {
            for (SseEmitter emitter : connections) {
                sendOrDrop(storeId, emitter, SseEmitter.event().name("heartbeat").data("ping"));
            }
        });
        // 비어 있는 매장 항목 정리
        for (Long storeId : storeConnections.keySet()) {
            storeConnections.computeIfPresent(storeId, (id, set) -> set.isEmpty() ? null : set);
        }
    }

    private void sendOrDrop(Long storeId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // 이미 끊긴 연결: 목록에서 빼고 남은 연결에 접속자 수를 다시 알린다
            log.debug("SSE 전송 실패 - 매장: {}, 오류: {}", storeId, e.getMessage());
            emitter.completeWithError(e);
            disconnect(storeId, emitter);
        }
    }

    public int viewerCount(Long storeId) {
        Set<SseEmitter> connections = storeConnections.get(storeId);
        return connections == null ? 0 : connections.size();
    }

    public ViewerCountEvent getCurrentViewerCount(Long storeId) {
        return viewerCountEvent(storeId, viewerCount(storeId), null);
    }

    /** 매장의 모든 SSE 연결을 끊는다(점주 전용). 클라이언트는 자동으로 다시 연결한다. */
    public ViewerCountEvent resetViewerCount(Long storeId) {
        Set<SseEmitter> connections = storeConnections.remove(storeId);
        if (connections != null) {
            connections.forEach(SseEmitter::complete);
        }
        log.info("매장 {} SSE 연결 초기화", storeId);
        return viewerCountEvent(storeId, 0, "접속자 연결을 초기화했습니다");
    }

    private static ViewerCountEvent viewerCountEvent(Long storeId, int count, String message) {
        return ViewerCountEvent.builder()
                .storeId(storeId)
                .viewerCount(count)
                .message(message != null ? message : "현재 " + count + "명이 메뉴를 보고 있습니다")
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
