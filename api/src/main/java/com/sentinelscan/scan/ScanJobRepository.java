package com.sentinelscan.scan;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ScanJobRepository {

    static final String INSERT_SQL = "INSERT INTO scan_job (id, target_url, status) VALUES (?, ?, 'queued')";
    static final String SELECT_SQL = "SELECT id, target_url, status, error, note, created_at, started_at, finished_at "
            + "FROM scan_job WHERE id = ?";

    private final JdbcTemplate jdbc;

    public ScanJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID enqueue(String targetUrl) {
        UUID id = UUID.randomUUID();
        jdbc.update(INSERT_SQL, id, targetUrl);
        return id;
    }

    public Optional<ScanJob> find(UUID id) {
        return jdbc.query(SELECT_SQL, (rs, i) -> new ScanJob(
                rs.getObject("id", UUID.class), rs.getString("target_url"), rs.getString("status"),
                rs.getString("error"), rs.getString("note"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("finished_at"))), id).stream().findFirst();
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
