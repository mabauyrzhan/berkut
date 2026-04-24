package kz.berkut.gateway.ws;

import kz.berkut.common.GpsPoint;
import kz.berkut.common.Topics;
import kz.berkut.common.VehicleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka → WebSocket bridge. Consumes both firehoses and broadcasts to STOMP topics.
 * No per-subscription filtering here — frontend does viewport-based filtering and
 * throttling (1 frame / 500 ms). Backend stays simple: forward everything, trust the client.
 *
 * For stricter backends (millions of points per second), add server-side viewport
 * subscriptions so each client only gets points in its map bbox.
 */
@Component
public class KafkaFanout {
    private static final Logger log = LoggerFactory.getLogger(KafkaFanout.class);

    private final SimpMessagingTemplate ws;
    private final AtomicLong gpsForwarded = new AtomicLong();
    private final AtomicLong eventsForwarded = new AtomicLong();

    public KafkaFanout(SimpMessagingTemplate ws) {
        this.ws = ws;
    }

    @KafkaListener(topics = Topics.GPS_POINTS, containerFactory = "gpsFanoutFactory")
    public void onGps(List<GpsPoint> batch) {
        if (batch.isEmpty()) return;
        for (GpsPoint p : batch) {
            ws.convertAndSend("/topic/gps", p);
        }
        long total = gpsForwarded.addAndGet(batch.size());
        if (total % 500 == 0 || total < 10) {
            log.info("gps forwarded: batch={} total={}", batch.size(), total);
        }
    }

    @KafkaListener(topics = Topics.EVENTS_DEDUPED, containerFactory = "eventsFanoutFactory")
    public void onEvents(List<VehicleEvent> batch) {
        if (batch.isEmpty()) return;
        for (VehicleEvent e : batch) {
            ws.convertAndSend("/topic/events", e);
        }
        long total = eventsForwarded.addAndGet(batch.size());
        log.info("events forwarded: batch={} total={}", batch.size(), total);
    }
}
