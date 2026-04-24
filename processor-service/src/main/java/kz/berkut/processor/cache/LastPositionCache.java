package kz.berkut.processor.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.berkut.common.GpsPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Hot-path cache used by the map:
 *   vehicle:last:{id}   — JSON of last GpsPoint, TTL 60s (auto-expires if feed stops)
 *   vehicle:online:{id} — heartbeat, TTL 30s (online-status decision: key exists = online)
 *
 * Pipelined to keep network round-trips flat across a batch.
 */
@Component
public class LastPositionCache {
    private static final Logger log = LoggerFactory.getLogger(LastPositionCache.class);

    private static final long LAST_TTL_SEC = 60;
    private static final long ONLINE_TTL_SEC = 30;
    private static final byte[] ONLINE_VALUE = "1".getBytes(StandardCharsets.UTF_8);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public LastPositionCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public void updateBatch(List<GpsPoint> points) {
        redis.executePipelined((RedisCallback<Object>) conn -> {
            for (GpsPoint p : points) {
                try {
                    byte[] json = mapper.writeValueAsBytes(p);
                    byte[] lastKey = ("vehicle:last:" + p.deviceId()).getBytes(StandardCharsets.UTF_8);
                    byte[] onlineKey = ("vehicle:online:" + p.deviceId()).getBytes(StandardCharsets.UTF_8);
                    conn.stringCommands().setEx(lastKey, LAST_TTL_SEC, json);
                    conn.stringCommands().setEx(onlineKey, ONLINE_TTL_SEC, ONLINE_VALUE);
                } catch (Exception e) {
                    log.warn("Failed to cache last position for {}: {}", p.deviceId(), e.getMessage());
                }
            }
            return null;
        });
    }
}
