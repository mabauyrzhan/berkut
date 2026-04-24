package kz.berkut.simulator.fleet;

import kz.berkut.common.GpsPoint;
import kz.berkut.common.Topics;
import kz.berkut.common.VehicleEvent;
import kz.berkut.simulator.config.SimulatorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class FleetScheduler {
    private static final Logger log = LoggerFactory.getLogger(FleetScheduler.class);

    private final SimulatorProperties props;
    private final KafkaTemplate<String, Object> kafka;
    private final AtomicLong gpsCount = new AtomicLong();
    private final AtomicLong eventCount = new AtomicLong();

    private volatile List<SimulatedVehicle> fleet;

    public FleetScheduler(SimulatorProperties props, KafkaTemplate<String, Object> kafka) {
        this.props = props;
        this.kafka = kafka;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        this.fleet = new VehicleFactory().create(props);
        // Publish metadata to the compacted registry so processor-service can hydrate vehicles table.
        for (SimulatedVehicle v : fleet) {
            kafka.send(Topics.VEHICLES_REGISTRY, v.deviceId(), v.meta());
        }
        log.info("Registered {} vehicles to {}", fleet.size(), Topics.VEHICLES_REGISTRY);
        log.info("Fleet initialized: size={} GPS interval={}s event rate={}/min/vehicle",
                fleet.size(), props.gps().intervalSeconds(), props.event().ratePerVehiclePerMin());
        fleet.stream().limit(3).forEach(v -> log.info("Sample vehicle: {}", v.meta()));
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        List<SimulatedVehicle> snapshot = fleet;
        if (snapshot == null) return;
        long now = System.currentTimeMillis();
        long gps = 0;
        long events = 0;
        for (SimulatedVehicle v : snapshot) {
            GpsPoint p = v.maybeEmitGps(now, props);
            if (p != null) {
                kafka.send(Topics.GPS_POINTS, v.deviceId(), p);
                gps++;
            }
            VehicleEvent e = v.maybeEmitEvent(now, props);
            if (e != null) {
                kafka.send(Topics.EVENTS_RAW, v.deviceId(), e);
                events++;
            }
        }
        gpsCount.addAndGet(gps);
        eventCount.addAndGet(events);
    }

    @Scheduled(fixedRate = 10000)
    public void logStats() {
        if (fleet == null) return;
        log.info("Cumulative produced: gps={} events={}", gpsCount.get(), eventCount.get());
    }
}
