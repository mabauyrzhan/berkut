package kz.berkut.processor.storage;

import kz.berkut.common.GpsPoint;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class GpsRepository {
    private static final String INSERT_SQL =
            "INSERT INTO gps_points(time, device_id, latitude, longitude, speed_kmh, heading_degrees) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;

    public GpsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertBatch(List<GpsPoint> points) {
        jdbc.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                GpsPoint p = points.get(i);
                ps.setTimestamp(1, Timestamp.from(p.timestamp()));
                ps.setString(2, p.deviceId());
                ps.setDouble(3, p.latitude());
                ps.setDouble(4, p.longitude());
                ps.setDouble(5, p.speedKmh());
                ps.setDouble(6, p.headingDegrees());
            }

            @Override
            public int getBatchSize() {
                return points.size();
            }
        });
    }
}
