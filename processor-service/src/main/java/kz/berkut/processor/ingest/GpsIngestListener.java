package kz.berkut.processor.ingest;

import kz.berkut.common.GpsPoint;
import kz.berkut.common.Topics;
import kz.berkut.processor.cache.LastPositionCache;
import kz.berkut.processor.storage.GpsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GpsIngestListener {
    private static final Logger log = LoggerFactory.getLogger(GpsIngestListener.class);

    private final GpsRepository repo;
    private final LastPositionCache cache;
    private final AtomicLong totalIngested = new AtomicLong();

    public GpsIngestListener(GpsRepository repo, LastPositionCache cache) {
        this.repo = repo;
        this.cache = cache;
    }

    @KafkaListener(topics = Topics.GPS_POINTS, containerFactory = "gpsBatchFactory")
    public void onBatch(List<GpsPoint> batch) {
        if (batch.isEmpty()) return;
        repo.insertBatch(batch);
        cache.updateBatch(batch);
        long total = totalIngested.addAndGet(batch.size());
        if (total % 500 == 0 || total < 50) {
            log.info("GPS ingested: batch={} total={}", batch.size(), total);
        }
    }
}
