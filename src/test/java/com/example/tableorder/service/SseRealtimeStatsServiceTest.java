package com.example.tableorder.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SseRealtimeStatsServiceTest {

    /** 보낸 이벤트 수를 세고, broken 이면 전송에 실패하는 emitter. */
    static class FakeEmitter extends SseEmitter {
        final AtomicInteger sent = new AtomicInteger();
        volatile boolean broken;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (broken) {
                throw new IOException("client gone");
            }
            sent.incrementAndGet();
        }
    }

    final Deque<FakeEmitter> next = new ArrayDeque<>();
    final List<FakeEmitter> created = new ArrayList<>();
    SseRealtimeStatsService service;

    @BeforeEach
    void setUp() {
        service = new SseRealtimeStatsService() {
            @Override
            SseEmitter createEmitter() {
                FakeEmitter e = next.isEmpty() ? new FakeEmitter() : next.poll();
                created.add(e);
                return e;
            }
        };
    }

    private FakeEmitter connect(long storeId) {
        service.createViewerCountStream(storeId);
        return created.get(created.size() - 1);
    }

    @Test
    void countFollowsOpenConnectionsPerStore() {
        connect(1);
        FakeEmitter b = connect(1);
        connect(2);

        assertThat(service.viewerCount(1L)).isEqualTo(2);
        assertThat(service.viewerCount(2L)).isEqualTo(1);

        service.disconnect(1L, b);
        assertThat(service.viewerCount(1L)).isEqualTo(1);
    }

    @Test
    void disconnectIsIdempotent() {
        FakeEmitter a = connect(1);
        connect(1);

        // onCompletion 과 onError 가 모두 불려도 한 번만 빠진다
        service.disconnect(1L, a);
        service.disconnect(1L, a);
        service.disconnect(1L, a);

        assertThat(service.viewerCount(1L)).isEqualTo(1);
    }

    @Test
    void joiningNotifiesExistingViewers() {
        FakeEmitter a = connect(1);
        int before = a.sent.get();
        connect(1);
        assertThat(a.sent.get()).isEqualTo(before + 1);
    }

    @Test
    void heartbeatDropsDeadConnections() {
        FakeEmitter alive = connect(1);
        FakeEmitter dead = connect(1);
        dead.broken = true;

        service.sendHeartbeats();

        assertThat(service.viewerCount(1L)).isEqualTo(1);
        assertThat(alive.sent.get()).isGreaterThan(0);
    }

    @Test
    void heartbeatIsSingleTaskNotPerConnection() {
        // 연결을 많이 만들고 끊어도 하트비트 한 번에 연결당 정확히 한 번만 보낸다
        for (int i = 0; i < 50; i++) {
            service.disconnect(1L, connect(1));
        }
        FakeEmitter survivor = connect(1);
        int before = survivor.sent.get();

        service.sendHeartbeats();

        assertThat(survivor.sent.get()).isEqualTo(before + 1);
        assertThat(service.viewerCount(1L)).isEqualTo(1);
    }

    @Test
    void resetClosesAllConnectionsOfStore() {
        connect(1);
        connect(1);
        connect(2);

        assertThat(service.resetViewerCount(1L).getViewerCount()).isZero();
        assertThat(service.viewerCount(1L)).isZero();
        assertThat(service.viewerCount(2L)).isEqualTo(1);
    }

    @Test
    void emptyStoresAreCleanedUp() throws Exception {
        FakeEmitter a = connect(1);
        service.disconnect(1L, a);
        service.sendHeartbeats();

        var field = SseRealtimeStatsService.class.getDeclaredField("storeConnections");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var connections = (java.util.Map<Long, Set<SseEmitter>>) field.get(service);
        assertThat(connections).doesNotContainKey(1L);
    }
}
