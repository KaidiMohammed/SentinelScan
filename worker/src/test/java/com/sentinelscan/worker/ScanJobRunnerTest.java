package com.sentinelscan.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScanJobRunnerTest {

    static class FakeStore implements ScanJobStore {
        Queue<Claimed> queue = new ArrayDeque<>();
        List<String> events = new ArrayList<>();
        public Optional<Claimed> claimNext() { return Optional.ofNullable(queue.poll()); }
        public void markDone(UUID id, String note) { events.add("done:" + note); }
        public void markFailed(UUID id, String error) { events.add("failed:" + error); }
    }

    @Test
    void emptyQueueDoesNothing() {
        assertFalse(new ScanJobRunner(new FakeStore(), List.of()).runOnce());
    }

    @Test
    void noRegisteredChecksIsDoneWithHonestNote() {
        FakeStore s = new FakeStore();
        s.queue.add(new ScanJobStore.Claimed(UUID.randomUUID(), "https://example.com"));
        assertTrue(new ScanJobRunner(s, List.of()).runOnce());
        assertEquals(List.of("done:" + ScanJobRunner.NO_CHECKS_NOTE), s.events);
    }

    @Test
    void checkExceptionMarksFailed() {
        FakeStore s = new FakeStore();
        s.queue.add(new ScanJobStore.Claimed(UUID.randomUUID(), "https://example.com"));
        ScanCheck boom = new ScanCheck() {
            public String key() { return "boom"; }
            public void run(UUID id, String url) { throw new IllegalStateException("kaput"); }
        };
        new ScanJobRunner(s, List.of(boom)).runOnce();
        assertEquals(List.of("failed:kaput"), s.events);
    }

    @Test
    void claimSqlUsesSkipLocked() {
        assertTrue(JdbcScanJobStore.CLAIM_SQL.contains("FOR UPDATE SKIP LOCKED"));
    }
}
