package kz.berkut.simulator.fleet;

import kz.berkut.common.EventType;
import kz.berkut.common.GpsPoint;
import kz.berkut.common.Severity;
import kz.berkut.common.Vehicle;
import kz.berkut.common.VehicleEvent;
import kz.berkut.simulator.config.SimulatorProperties;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class SimulatedVehicle {
    private static final EventType[] EVENT_TYPES = EventType.values();
    private static final double KM_PER_DEG_LAT = 111.0;

    private final Vehicle meta;
    private final Random random;

    private double lat;
    private double lon;
    private double headingDeg;
    private double speedKmh;

    private long lastGpsAtMs;
    private long nextGpsAtMs;

    private int burstRemaining;
    private EventType burstType;
    private long burstNextAtMs;

    public SimulatedVehicle(Vehicle meta, double lat, double lon, double heading, double speed,
                            long initialGpsOffsetMs, Random parentRand) {
        this.meta = meta;
        this.random = new Random(parentRand.nextLong());
        this.lat = lat;
        this.lon = lon;
        this.headingDeg = heading;
        this.speedKmh = speed;
        this.nextGpsAtMs = System.currentTimeMillis() + initialGpsOffsetMs;
    }

    public Vehicle meta() { return meta; }
    public String deviceId() { return meta.deviceId(); }

    public GpsPoint maybeEmitGps(long nowMs, SimulatorProperties props) {
        if (nowMs < nextGpsAtMs) return null;

        if (lastGpsAtMs > 0) {
            advancePosition(nowMs, props);
        }
        lastGpsAtMs = nowMs;

        long intervalMs = props.gps().intervalSeconds() * 1000L;
        long jitter = props.gps().jitterMs() > 0
                ? random.nextInt(props.gps().jitterMs() * 2 + 1) - props.gps().jitterMs()
                : 0;
        nextGpsAtMs = nowMs + intervalMs + jitter;

        return new GpsPoint(meta.deviceId(), Instant.ofEpochMilli(nowMs), lat, lon, speedKmh, headingDeg);
    }

    public VehicleEvent maybeEmitEvent(long nowMs, SimulatorProperties props) {
        // Burst path: same-type events every ~2s for hysteresis demo.
        if (burstRemaining > 0 && nowMs >= burstNextAtMs) {
            burstRemaining--;
            burstNextAtMs = nowMs + 2000 + random.nextInt(1000);
            return newEvent(nowMs, burstType);
        }

        // Normal Poisson per-second sampling.
        double perSecondRate = props.event().ratePerVehiclePerMin() / 60.0;
        if (random.nextDouble() < perSecondRate) {
            EventType type = EVENT_TYPES[random.nextInt(EVENT_TYPES.length)];
            if (random.nextDouble() < props.event().burstProbability()) {
                burstType = type;
                burstRemaining = 3 + random.nextInt(5);  // 3..7 follow-ups
                burstNextAtMs = nowMs + 2000;
            }
            return newEvent(nowMs, type);
        }
        return null;
    }

    private void advancePosition(long nowMs, SimulatorProperties props) {
        double deltaSec = (nowMs - lastGpsAtMs) / 1000.0;
        double distanceKm = (speedKmh / 3600.0) * deltaSec;
        double cosLat = Math.cos(Math.toRadians(lat));
        double dLat = distanceKm * Math.cos(Math.toRadians(headingDeg)) / KM_PER_DEG_LAT;
        double dLon = distanceKm * Math.sin(Math.toRadians(headingDeg)) / (KM_PER_DEG_LAT * cosLat);
        lat += dLat;
        lon += dLon;

        // Heading drift ±15° per tick.
        headingDeg = (headingDeg + (random.nextDouble() - 0.5) * 30 + 360) % 360;
        // Speed drift ±2.5 km/h, clamped.
        speedKmh = clamp(speedKmh + (random.nextDouble() - 0.5) * 5, 20, 120);

        // Bounce off bbox walls.
        double r = props.bbox().radiusKm() / KM_PER_DEG_LAT;
        double rLon = r / Math.cos(Math.toRadians(props.bbox().centerLat()));
        if (lat > props.bbox().centerLat() + r || lat < props.bbox().centerLat() - r
                || lon > props.bbox().centerLon() + rLon || lon < props.bbox().centerLon() - rLon) {
            headingDeg = (headingDeg + 180) % 360;
            lat = clamp(lat, props.bbox().centerLat() - r, props.bbox().centerLat() + r);
            lon = clamp(lon, props.bbox().centerLon() - rLon, props.bbox().centerLon() + rLon);
        }
    }

    private VehicleEvent newEvent(long nowMs, EventType type) {
        return new VehicleEvent(
                UUID.randomUUID().toString(),
                meta.deviceId(),
                Instant.ofEpochMilli(nowMs),
                type,
                rollSeverity(),
                lat, lon, speedKmh,
                Map.of("source", "simulator")
        );
    }

    private Severity rollSeverity() {
        double r = random.nextDouble();
        if (r < 0.6) return Severity.LOW;
        if (r < 0.9) return Severity.MEDIUM;
        return Severity.CRITICAL;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
