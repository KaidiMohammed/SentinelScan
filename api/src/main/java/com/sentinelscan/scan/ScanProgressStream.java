package com.sentinelscan.scan;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Progression en direct par SSE. Lit l'état réel en base (poll-and-diff) : un événement « progress »
 * n'est émis que lorsque le job ou une vérification a réellement changé. Rien n'est simulé.
 */
@RestController
@RequestMapping("/api/scans")
public class ScanProgressStream {

    static final long POLL_MS = 1000;
    static final long MAX_STREAM_MS = TimeUnit.MINUTES.toMillis(15);

    /** Charge utile d'un événement : état du job + une entrée par vérification réellement enregistrée. */
    public record Snapshot(ScanJob job, List<ScanCheckRow> checks) {
        boolean terminal() {
            return "done".equals(job.status()) || "failed".equals(job.status());
        }
    }

    private final ScanJobRepository repository;
    private final ScheduledExecutorService poller = Executors.newScheduledThreadPool(2);

    public ScanProgressStream(ScanJobRepository repository) {
        this.repository = repository;
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@PathVariable UUID id) {
        if (snapshot(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        SseEmitter emitter = new SseEmitter(MAX_STREAM_MS);
        AtomicReference<Snapshot> last = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> task = new AtomicReference<>();
        Runnable cancel = () -> {
            ScheduledFuture<?> f = task.get();
            if (f != null) {
                f.cancel(false);
            }
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(cancel);
        emitter.onError(e -> cancel.run());
        task.set(poller.scheduleWithFixedDelay(() -> tick(id, emitter, last, cancel), 0, POLL_MS, TimeUnit.MILLISECONDS));
        return ResponseEntity.ok(emitter);
    }

    private void tick(UUID id, SseEmitter emitter, AtomicReference<Snapshot> last, Runnable cancel) {
        try {
            Optional<Snapshot> current = snapshot(id);
            if (current.isEmpty()) {
                emitter.complete();
                cancel.run();
                return;
            }
            Snapshot s = current.get();
            if (changed(last.get(), s)) {
                emitter.send(SseEmitter.event().name("progress").data(s, MediaType.APPLICATION_JSON));
                last.set(s);
            }
            if (s.terminal()) {
                emitter.complete();
                cancel.run();
            }
        } catch (IOException e) {
            cancel.run(); // client parti
        } catch (RuntimeException e) {
            // Erreur réelle (ex. base indisponible) : on la signale, on ne fabrique pas d'état.
            try {
                emitter.send(SseEmitter.event().name("stream-error").data("Lecture de la progression impossible"));
            } catch (IOException ignored) {
                // rien à faire
            }
            emitter.completeWithError(e);
            cancel.run();
        }
    }

    Optional<Snapshot> snapshot(UUID id) {
        return repository.find(id).map(job -> new Snapshot(job, repository.findChecks(id)));
    }

    static boolean changed(Snapshot previous, Snapshot current) {
        return previous == null || !previous.equals(current);
    }
}
