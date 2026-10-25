package com.sentinelscan.scan;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scans")
public class ScanController {

    public record CreateScanRequest(String url) {
    }

    private final ScanJobRepository repository;

    public ScanController(ScanJobRepository repository) {
        this.repository = repository;
    }

    /** Crée un job « queued ». Aucun résultat n'est produit ici : c'est le worker qui exécute. */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateScanRequest request) {
        String url = request == null ? null : request.url();
        if (!isHttpUrl(url)) {
            return ResponseEntity.badRequest().body(Map.of("error", "url must be an absolute http(s) URL"));
        }
        UUID id = repository.enqueue(url);
        return ResponseEntity.created(URI.create("/api/scans/" + id))
                .body(Map.of("id", id, "status", "queued"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScanJob> get(@PathVariable UUID id) {
        return repository.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    static boolean isHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
