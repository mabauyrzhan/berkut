package kz.berkut.processor.dedup;

import kz.berkut.common.Severity;
import kz.berkut.common.Topics;
import kz.berkut.common.VehicleEvent;
import kz.berkut.processor.storage.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Windowed dedup + grouping of raw events.
 *
 * For each event we try to claim a dedup key in Redis:
 *   - NEW   → create group row, publish to events.deduped with isNew=true
 *   - EXISTING → increment group counters, publish with isNew=false (same groupId)
 *
 * Any CRITICAL event is also forked to events.critical for the webhook path. Notifier
 * dedups downstream by Idempotency-Key = eventId, so five CRITICAL events in a burst
 * yield five POSTs per spec (grouping of webhook calls would be a separate design choice).
 *
 * Ordering guarantee: events with the same device_id go to the same partition, so a
 * single consumer thread owns all events for any given (device_id, type) pair.
 */
@Component
public class DedupListener {
    private static final Logger log = LoggerFactory.getLogger(DedupListener.class);

    private final DedupScript dedup;
    private final EventGroupRepository groupRepo;
    private final EventRepository eventRepo;
    private final KafkaTemplate<String, Object> kafka;
    private final long windowSeconds;

    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong newGroups = new AtomicLong();
    private final AtomicLong criticals = new AtomicLong();

    public DedupListener(
            DedupScript dedup,
            EventGroupRepository groupRepo,
            EventRepository eventRepo,
            KafkaTemplate<String, Object> kafka,
            @Value("${dedup.window.seconds:30}") long windowSeconds
    ) {
        this.dedup = dedup;
        this.groupRepo = groupRepo;
        this.eventRepo = eventRepo;
        this.kafka = kafka;
        this.windowSeconds = windowSeconds;
    }

    @KafkaListener(topics = Topics.EVENTS_RAW, containerFactory = "rawEventBatchFactory")
    public void onBatch(List<VehicleEvent> batch) {
        if (batch.isEmpty()) return;

        List<VehicleEvent> enriched = new ArrayList<>(batch.size());

        for (VehicleEvent event : batch) {
            String key = "dedup:" + event.deviceId() + ":" + event.type().name();
            UUID candidate = UUID.randomUUID();
            DedupScript.DedupResult r = dedup.claim(key, candidate.toString(), windowSeconds);
            UUID groupId = UUID.fromString(r.groupId());

            if (r.isNew()) {
                groupRepo.insert(groupId, event);
                newGroups.incrementAndGet();
            } else {
                groupRepo.touch(groupId, event);
            }

            VehicleEvent out = withGroup(event, groupId, r.isNew());
            enriched.add(out);

            kafka.send(Topics.EVENTS_DEDUPED, event.deviceId(), out);
            if (event.severity() == Severity.CRITICAL) {
                kafka.send(Topics.EVENTS_CRITICAL, event.deviceId(), out);
                criticals.incrementAndGet();
            }
        }

        // One batched insert for all raw events (with groupId fk filled in via metadata).
        eventRepo.insertBatch(enriched);

        long total = processed.addAndGet(batch.size());
        if (total % 100 == 0 || total < 20) {
            log.info("dedup: batch={} total={} new_groups={} criticals={}",
                    batch.size(), total, newGroups.get(), criticals.get());
        }
    }

    private static VehicleEvent withGroup(VehicleEvent event, UUID groupId, boolean isNew) {
        Map<String, Object> meta = new HashMap<>(event.metadata() != null ? event.metadata() : Map.of());
        meta.put("groupId", groupId.toString());
        meta.put("isNew", isNew);
        return new VehicleEvent(
                event.eventId(), event.deviceId(), event.timestamp(),
                event.type(), event.severity(),
                event.latitude(), event.longitude(), event.speedKmh(),
                meta
        );
    }
}
