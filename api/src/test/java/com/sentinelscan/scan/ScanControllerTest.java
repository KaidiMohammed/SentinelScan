package com.sentinelscan.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ScanControllerTest {

    /** Repository en mémoire : teste le contrôleur sans Postgres. */
    static class FakeRepo extends ScanJobRepository {
        UUID last;
        FakeRepo() { super(null); }
        @Override public UUID enqueue(String url) { last = UUID.randomUUID(); return last; }
        @Override public java.util.Optional<ScanJob> find(UUID id) { return java.util.Optional.empty(); }
    }

    @Test
    void validUrlCreatesQueuedJob() {
        FakeRepo repo = new FakeRepo();
        ResponseEntity<?> r = new ScanController(repo).create(new ScanController.CreateScanRequest("https://example.com"));
        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        assertTrue(r.getBody().toString().contains("queued"));
    }

    @Test
    void invalidUrlRejected() {
        ScanController c = new ScanController(new FakeRepo());
        assertEquals(HttpStatus.BAD_REQUEST, c.create(new ScanController.CreateScanRequest("not a url")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, c.create(new ScanController.CreateScanRequest("ftp://x.com")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, c.create(null).getStatusCode());
    }

    @Test
    void unknownJobIs404() {
        assertEquals(HttpStatus.NOT_FOUND, new ScanController(new FakeRepo()).get(UUID.randomUUID()).getStatusCode());
    }

    @Test
    void urlValidation() {
        assertTrue(ScanController.isHttpUrl("http://a.io/x"));
        assertFalse(ScanController.isHttpUrl("file:///etc/passwd"));
    }
}
