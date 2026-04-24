package kz.berkut.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("simulator")
public record SimulatorProperties(
        Fleet fleet,
        Gps gps,
        Event event,
        Bbox bbox
) {
    public record Fleet(int size, long seed) {}

    public record Gps(int intervalSeconds, int jitterMs) {}

    /**
     * ratePerVehiclePerMin — Poisson-like average rate of normal events.
     * burstProbability — chance that a normal event also triggers a burst (3-7 follow-ups
     * of the same type within seconds), so the dedup hysteresis path gets exercised.
     */
    public record Event(double ratePerVehiclePerMin, double burstProbability) {}

    public record Bbox(double centerLat, double centerLon, double radiusKm) {}
}
