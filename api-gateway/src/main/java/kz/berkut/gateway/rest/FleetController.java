package kz.berkut.gateway.rest;

import kz.berkut.common.Vehicle;
import kz.berkut.gateway.dto.Views.DashboardStats;
import kz.berkut.gateway.dto.Views.EventGroupView;
import kz.berkut.gateway.dto.Views.PositionView;
import kz.berkut.gateway.dto.Views.VehicleDetail;
import kz.berkut.gateway.storage.EventsRepository;
import kz.berkut.gateway.storage.PositionsCache;
import kz.berkut.gateway.storage.VehicleRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FleetController {

    private final EventsRepository events;
    private final VehicleRepository vehicles;
    private final PositionsCache positions;

    public FleetController(EventsRepository events, VehicleRepository vehicles, PositionsCache positions) {
        this.events = events;
        this.vehicles = vehicles;
        this.positions = positions;
    }

    @GetMapping("/events")
    public List<EventGroupView> events(
            @RequestParam(required = false) String vehicle,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(defaultValue = "500") int limit
    ) {
        return events.query(vehicle, type, severity, since, until, limit);
    }

    @GetMapping("/positions/last")
    public List<PositionView> lastPositions() {
        return positions.all();
    }

    @GetMapping("/vehicles/{id}")
    public VehicleDetail vehicle(@PathVariable String id) {
        Vehicle v = vehicles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PositionView last = positions.get(id).orElse(null);
        List<EventGroupView> recent = events.query(id, null, null, null, null, 10);
        return new VehicleDetail(v.deviceId(), v.licensePlate(), v.driverName(), last, recent);
    }

    @GetMapping("/dashboard/stats")
    public DashboardStats dashboardStats() {
        return new DashboardStats(
                positions.onlineCount(),
                vehicles.count(),
                events.countLastHour(),
                events.countLastDay(),
                events.countCriticalLastDay()
        );
    }

    @GetMapping(value = "/events/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @RequestParam(required = false) String vehicle,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until
    ) {
        List<EventGroupView> rows = events.query(vehicle, type, severity, since, until, 5000);
        StreamingResponseBody body = out -> {
            try (OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                w.write("group_id,device_id,license_plate,driver_name,type,severity,first_seen,last_seen,event_count\n");
                for (EventGroupView r : rows) {
                    w.write(String.join(",",
                            nullSafe(r.groupId()),
                            nullSafe(r.deviceId()),
                            nullSafe(r.licensePlate()),
                            csvEscape(r.driverName()),
                            nullSafe(r.type()),
                            nullSafe(r.severity()),
                            r.firstSeen().toString(),
                            r.lastSeen().toString(),
                            String.valueOf(r.eventCount())));
                    w.write("\n");
                }
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header("Content-Disposition", "attachment; filename=\"events.csv\"")
                .body(body);
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
