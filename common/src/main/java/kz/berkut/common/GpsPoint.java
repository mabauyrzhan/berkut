package kz.berkut.common;

import java.time.Instant;

public record GpsPoint(
        String deviceId,
        Instant timestamp,
        double latitude,
        double longitude,
        double speedKmh,
        double headingDegrees
) {}
