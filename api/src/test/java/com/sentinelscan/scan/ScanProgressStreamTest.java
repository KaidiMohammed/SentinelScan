package com.sentinelscan.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ScanProgressStreamTest {

    static class FakeRepo extends ScanJobRepository {
        ScanJob job;
        List<ScanCheckRow> checks = new ArrayList<>();
        FakeRepo() { super(null); }
        @Override public Optional<ScanJob> find(UUID id) { return Optional.ofNullable(job); }
        @Override public List<ScanCheckRow> findChecks(UUID id) { return List.copyOf(checks); }
    }

    static ScanJob job(String status, String note) {
        return new ScanJob(UUID.randomUUID(), "https://example.com", status, null, note, null, null, null);
    }

    @Test
    void unknownJobIs404() {
        assertEquals(HttpStatus.NOT_FOUND,
                new ScanProgressStream(new FakeRepo()).events(UUID.randomUUID()).getStatusCode());
    }

    @Test
    void doneJobWithNoChecksHasEmptyCheckListAndIsTerminal() {
        FakeRepo repo = new FakeRepo();
        repo.job = job("done", "Nothing was checked");
        ScanProgressStream.Snapshot s = new ScanProgressStream(repo).snapshot(repo.job.id()).orElseThrow();
        assertTrue(s.checks().isEmpty());
        assertTrue(s.terminal());
        assertEquals("Nothing was checked", s.job().note());
    }

    @Test
    void runningJobIsNotTerminal() {
        FakeRepo repo = new FakeRepo();
        repo.job = job("running", null);
        assertFalse(new ScanProgressStream(repo).snapshot(repo.job.id()).orElseThrow().terminal());
    }

    @Test
    void changedDetectsOnlyRealChanges() {
        FakeRepo repo = new FakeRepo();
        repo.job = job("running", null);
        ScanProgressStream s = new ScanProgressStream(repo);
        ScanProgressStream.Snapshot a = s.snapshot(repo.job.id()).orElseThrow();
        assertTrue(ScanProgressStream.changed(null, a));
        assertFalse(ScanProgressStream.changed(a, s.snapshot(repo.job.id()).orElseThrow()));
        repo.checks.add(new ScanCheckRow("headers", "not_checked", "timeout"));
        assertTrue(ScanProgressStream.changed(a, s.snapshot(repo.job.id()).orElseThrow()));
    }
}
