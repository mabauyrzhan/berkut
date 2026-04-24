package kz.berkut.processor.storage;

import kz.berkut.common.Vehicle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VehicleRepository {
    private static final String UPSERT_SQL =
            "INSERT INTO vehicles(device_id, license_plate, driver_name) VALUES (?, ?, ?) "
                    + "ON CONFLICT (device_id) DO UPDATE SET "
                    + "license_plate = EXCLUDED.license_plate, "
                    + "driver_name = EXCLUDED.driver_name, "
                    + "updated_at = now()";

    private final JdbcTemplate jdbc;

    public VehicleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(Vehicle vehicle) {
        jdbc.update(UPSERT_SQL, vehicle.deviceId(), vehicle.licensePlate(), vehicle.driverName());
    }
}
