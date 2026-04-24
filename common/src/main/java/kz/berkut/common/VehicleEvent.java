package kz.berkut.common;

import java.time.Instant;
import java.util.Map;

public record VehicleEvent(
        String eventId,
        String deviceId,
        Instant timestamp,
        EventType type,
        Severity severity,
        double latitude,
        double longitude,
        double speedKmh,
        Map<String, Object> metadata
) {}
