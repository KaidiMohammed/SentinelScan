package com.sentinelscan.worker;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Interroge la file Postgres (SELECT ... FOR UPDATE SKIP LOCKED) et exécute les vérifications enregistrées.
 * Phase 1 : aucune vérification n'est enregistrée ; le runner n'invente aucun résultat.
 */
@Component
public class ScanJobRunner {

    static final String NO_CHECKS_NOTE = "No security checks are implemented yet; nothing was checked.";

    private static final Logger log = LoggerFactory.getLogger(ScanJobRunner.class);

    private final ScanJobStore store;
    private final List<ScanCheck> checks;

    public ScanJobRunner(ScanJobStore store, List<ScanCheck> checks) {
        this.store = store;
        this.checks = checks;
    }

    @Scheduled(fixedDelayString = "${sentinelscan.worker.poll-interval-ms:2000}")
    public void poll() {
        while (runOnce()) {
            // vide la file avant de dormir
        }
    }

    /** @return true si un job a été traité. */
    boolean runOnce() {
        var claimed = store.claimNext();
        if (claimed.isEmpty()) {
            return false;
        }
        var job = claimed.get();
        try {
            for (ScanCheck check : checks) {
                check.run(job.id(), job.targetUrl());
            }
            store.markDone(job.id(), checks.isEmpty() ? NO_CHECKS_NOTE : null);
        } catch (Exception e) {
            log.error("scan job {} failed", job.id(), e);
            store.markFailed(job.id(), String.valueOf(e.getMessage()));
        }
        return true;
    }
}
