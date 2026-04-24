package kz.berkut.gateway.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.berkut.common.GpsPoint;
import kz.berkut.common.Vehicle;
import kz.berkut.gateway.dto.Views.PositionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PositionsCache {
    private static final Logger log = LoggerFactory.getLogger(PositionsCache.class);
    private static final String LAST_PREFIX = "vehicle:last:";
    private static final String ONLINE_PREFIX = "vehicle:online:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final VehicleRepository vehicles;

    public PositionsCache(StringRedisTemplate redis, ObjectMapper mapper, VehicleRepository vehicles) {
        this.redis = redis;
        this.mapper = mapper;
        this.vehicles = vehicles;
    }

    /**
     * Returns last position for every device that still has an entry in Redis (not expired).
     * Uses KEYS for simplicity — fine at 1k vehicles; for production scale (>50k keys)
     * switch to SCAN to avoid blocking Redis while it walks the keyspace.
     */
    public List<PositionView> all() {
        Set<String> lastKeys = redis.keys(LAST_PREFIX + "*");
        if (lastKeys == null || lastKeys.isEmpty()) return List.of();

        Set<String> onlineKeys = redis.keys(ONLINE_PREFIX + "*");
        Set<String> onlineDevices = onlineKeys == null ? Set.of()
                : onlineKeys.stream().map(k -> k.substring(ONLINE_PREFIX.length())).collect(Collectors.toSet());

        List<String> orderedKeys = new ArrayList<>(lastKeys);
        List<String> values = redis.opsForValue().multiGet(orderedKeys);
        if (values == null) values = Collections.nCopies(orderedKeys.size(), null);

        Map<String, Vehicle> meta = vehicles.findAllIndexed();
        List<PositionView> result = new ArrayList<>(orderedKeys.size());

        for (int i = 0; i < orderedKeys.size(); i++) {
            String deviceId = orderedKeys.get(i).substring(LAST_PREFIX.length());
            String json = values.get(i);
            if (json == null) continue;
            try {
                GpsPoint p = mapper.readValue(json, GpsPoint.class);
                Vehicle v = meta.get(deviceId);
                String plate = v != null ? v.licensePlate() : "?";
                String driver = v != null ? v.driverName() : "?";
                result.add(new PositionView(
                        deviceId, plate, driver,
                        p.latitude(), p.longitude(), p.speedKmh(), p.headingDegrees(),
                        p.timestamp(), onlineDevices.contains(deviceId)));
            } catch (Exception e) {
                log.warn("skipping {}: {}", deviceId, e.getMessage());
            }
        }
        return result;
    }

    public Optional<PositionView> get(String deviceId) {
        String json = redis.opsForValue().get(LAST_PREFIX + deviceId);
        if (json == null) return Optional.empty();
        Boolean online = redis.hasKey(ONLINE_PREFIX + deviceId);
        try {
            GpsPoint p = mapper.readValue(json, GpsPoint.class);
            Optional<Vehicle> v = vehicles.findById(deviceId);
            return Optional.of(new PositionView(
                    deviceId,
                    v.map(Vehicle::licensePlate).orElse("?"),
                    v.map(Vehicle::driverName).orElse("?"),
                    p.latitude(), p.longitude(), p.speedKmh(), p.headingDegrees(),
                    p.timestamp(), Boolean.TRUE.equals(online)));
        } catch (Exception e) {
            log.warn("parse error for {}: {}", deviceId, e.getMessage());
            return Optional.empty();
        }
    }

    public long onlineCount() {
        Set<String> keys = redis.keys(ONLINE_PREFIX + "*");
        return keys == null ? 0 : keys.size();
    }
}
