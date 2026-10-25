package com.sentinelscan.worker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcScanJobStore implements ScanJobStore {

    static final String CLAIM_SQL = "SELECT id, target_url FROM scan_job WHERE status = 'queued' "
            + "ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED";
    static final String START_SQL = "UPDATE scan_job SET status = 'running', started_at = now() WHERE id = ?";

    private final JdbcTemplate jdbc;

    public JdbcScanJobStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Optional<Claimed> claimNext() {
        List<Claimed> rows = jdbc.query(CLAIM_SQL,
                (rs, i) -> new Claimed(rs.getObject("id", UUID.class), rs.getString("target_url")));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        jdbc.update(START_SQL, rows.get(0).id());
        return Optional.of(rows.get(0));
    }

    @Override
    public void markDone(UUID id, String note) {
        jdbc.update("UPDATE scan_job SET status = 'done', note = ?, finished_at = now() WHERE id = ?", note, id);
    }

    @Override
    public void markFailed(UUID id, String error) {
        jdbc.update("UPDATE scan_job SET status = 'failed', error = ?, finished_at = now() WHERE id = ?", error, id);
    }
}
