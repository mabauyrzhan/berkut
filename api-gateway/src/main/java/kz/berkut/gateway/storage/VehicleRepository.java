package kz.berkut.gateway.storage;

import kz.berkut.common.Vehicle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class VehicleRepository {

    private final JdbcTemplate jdbc;

    public VehicleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Vehicle> findById(String deviceId) {
        return jdbc.query(
                "SELECT device_id, license_plate, driver_name FROM vehicles WHERE device_id = ?",
                (rs, n) -> new Vehicle(rs.getString(1), rs.getString(2), rs.getString(3)),
                deviceId
        ).stream().findFirst();
    }

    public Map<String, Vehicle> findAllIndexed() {
        return jdbc.query(
                        "SELECT device_id, license_plate, driver_name FROM vehicles",
                        (rs, n) -> new Vehicle(rs.getString(1), rs.getString(2), rs.getString(3))
                ).stream()
                .collect(Collectors.toMap(Vehicle::deviceId, v -> v));
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM vehicles", Long.class);
    }
}
