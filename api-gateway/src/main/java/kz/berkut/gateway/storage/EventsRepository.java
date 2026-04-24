package kz.berkut.gateway.storage;

import kz.berkut.gateway.dto.Views.EventGroupView;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class EventsRepository {

    /**
     * The journal view: one row per group. Joined with vehicles for display names.
     * Time filter uses last_seen so a still-active group shows up even if first_seen
     * is older — matches dispatcher expectation ("what's happening now").
     */
    // Explicit casts keep Postgres happy when a param is null — otherwise pgjdbc sends
    // `$n` untyped and the planner fails with "could not determine data type".
    private static final String QUERY_GROUPS = """
            SELECT g.group_id, g.device_id, v.license_plate, v.driver_name,
                   g.type, g.severity, g.first_seen, g.last_seen, g.event_count
            FROM event_groups g
            LEFT JOIN vehicles v ON v.device_id = g.device_id
            WHERE (cast(:since    AS timestamptz) IS NULL OR g.last_seen >= cast(:since AS timestamptz))
              AND (cast(:until    AS timestamptz) IS NULL OR g.last_seen <= cast(:until AS timestamptz))
              AND (cast(:device   AS varchar)     IS NULL OR g.device_id = cast(:device AS varchar))
              AND (cast(:type     AS varchar)     IS NULL OR g.type      = cast(:type AS varchar))
              AND (cast(:severity AS varchar)     IS NULL OR g.severity  = cast(:severity AS varchar))
            ORDER BY g.last_seen DESC
            LIMIT :limit
            """;

    private static final String COUNT_LAST_HOUR =
            "SELECT COUNT(*) FROM events WHERE time >= now() - INTERVAL '1 hour'";
    private static final String COUNT_LAST_DAY =
            "SELECT COUNT(*) FROM events WHERE time >= now() - INTERVAL '1 day'";
    private static final String COUNT_CRITICAL_LAST_DAY =
            "SELECT COUNT(*) FROM events WHERE severity = 'CRITICAL' AND time >= now() - INTERVAL '1 day'";

    private final NamedParameterJdbcTemplate jdbc;

    public EventsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EventGroupView> query(String device, String type, String severity,
                                      Instant since, Instant until, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("device", device)
                .addValue("type", type)
                .addValue("severity", severity)
                .addValue("since", since == null ? null : Timestamp.from(since))
                .addValue("until", until == null ? null : Timestamp.from(until))
                .addValue("limit", Math.min(Math.max(limit, 1), 5000));
        return jdbc.query(QUERY_GROUPS, p, EventsRepository::mapRow);
    }

    public long countLastHour() { return jdbc.getJdbcTemplate().queryForObject(COUNT_LAST_HOUR, Long.class); }

    public long countLastDay() { return jdbc.getJdbcTemplate().queryForObject(COUNT_LAST_DAY, Long.class); }

    public long countCriticalLastDay() {
        return jdbc.getJdbcTemplate().queryForObject(COUNT_CRITICAL_LAST_DAY, Long.class);
    }

    private static EventGroupView mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new EventGroupView(
                rs.getString("group_id"),
                rs.getString("device_id"),
                rs.getString("license_plate"),
                rs.getString("driver_name"),
                rs.getString("type"),
                rs.getString("severity"),
                rs.getTimestamp("first_seen").toInstant(),
                rs.getTimestamp("last_seen").toInstant(),
                rs.getInt("event_count")
        );
    }
}
