package kz.berkut.gateway.dto;

import java.time.Instant;
import java.util.List;

public final class Views {

    public record EventGroupView(
            String groupId,
            String deviceId,
            String licensePlate,
            String driverName,
            String type,
            String severity,
            Instant firstSeen,
            Instant lastSeen,
            int eventCount
    ) {}

    public record PositionView(
            String deviceId,
            String licensePlate,
            String driverName,
            double latitude,
            double longitude,
            double speedKmh,
            double headingDegrees,
            Instant timestamp,
            boolean online
    ) {}

    public record VehicleDetail(
            String deviceId,
            String licensePlate,
            String driverName,
            PositionView lastPosition,
            List<EventGroupView> recentEvents
    ) {}

    public record DashboardStats(
            long onlineVehicles,
            long totalVehicles,
            long eventsLastHour,
            long eventsLastDay,
            long criticalLastDay
    ) {}

    private Views() {}
}
