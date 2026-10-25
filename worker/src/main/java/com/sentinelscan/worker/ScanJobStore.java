package com.sentinelscan.worker;

import java.util.Optional;
import java.util.UUID;

public interface ScanJobStore {

    record Claimed(UUID id, String targetUrl) {
    }

    /** Réclame atomiquement le plus ancien job queued et le passe en running. */
    Optional<Claimed> claimNext();

    void markDone(UUID id, String note);

    void markFailed(UUID id, String error);
}
