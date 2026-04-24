package kz.berkut.processor.dedup;

import kz.berkut.common.VehicleEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.UUID;

@Repository
public class EventGroupRepository {

    private static final String INSERT_SQL =
            "INSERT INTO event_groups(group_id, device_id, type, severity, first_seen, last_seen, event_count) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 1) ON CONFLICT (group_id) DO NOTHING";

    // Inline severity priority inside SQL keeps this one round-trip and race-free per row.
    // Same device+type events go to the same Kafka partition, so only one dedup thread
    // ever touches a given group at a time — extra locking would be noise.
    private static final String TOUCH_SQL = """
            UPDATE event_groups
            SET event_count = event_count + 1,
                last_seen   = GREATEST(last_seen, ?),
                severity    = CASE
                    WHEN (CASE ?        WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'CRITICAL' THEN 3 ELSE 0 END) >
                         (CASE severity WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2 WHEN 'CRITICAL' THEN 3 ELSE 0 END)
                    THEN ?
                    ELSE severity
                END
            WHERE group_id = ?
            """;

    private final JdbcTemplate jdbc;

    public EventGroupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID groupId, VehicleEvent event) {
        Timestamp ts = Timestamp.from(event.timestamp());
        jdbc.update(INSERT_SQL,
                groupId,
                event.deviceId(),
                event.type().name(),
                event.severity().name(),
                ts, ts);
    }

    public void touch(UUID groupId, VehicleEvent event) {
        jdbc.update(TOUCH_SQL,
                Timestamp.from(event.timestamp()),
                event.severity().name(),
                event.severity().name(),
                groupId);
    }
}
