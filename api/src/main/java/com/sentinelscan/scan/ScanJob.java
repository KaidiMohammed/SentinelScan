package com.sentinelscan.scan;

import java.time.Instant;
import java.util.UUID;

public record ScanJob(UUID id, String targetUrl, String status, String error, String note,
                      Instant createdAt, Instant startedAt, Instant finishedAt) {
}
