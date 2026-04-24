package kz.berkut.processor.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.berkut.common.VehicleEvent;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Repository
public class EventRepository {
    private static final String INSERT_SQL =
            "INSERT INTO events(time, event_id, device_id, type, severity, latitude, longitude, speed_kmh, group_id, metadata) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT DO NOTHING";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public EventRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void insertBatch(List<VehicleEvent> events) {
        jdbc.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                VehicleEvent e = events.get(i);
                ps.setTimestamp(1, Timestamp.from(e.timestamp()));
                ps.setObject(2, UUID.fromString(e.eventId()));
                ps.setString(3, e.deviceId());
                ps.setString(4, e.type().name());
                ps.setString(5, e.severity().name());
                ps.setDouble(6, e.latitude());
                ps.setDouble(7, e.longitude());
                ps.setDouble(8, e.speedKmh());
                Object groupId = e.metadata() != null ? e.metadata().get("groupId") : null;
                if (groupId != null) {
                    ps.setObject(9, UUID.fromString(groupId.toString()));
                } else {
                    ps.setNull(9, Types.OTHER);
                }
                ps.setString(10, serializeMetadata(e));
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        });
    }

    private String serializeMetadata(VehicleEvent e) {
        if (e.metadata() == null) return "{}";
        try {
            return mapper.writeValueAsString(e.metadata());
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
