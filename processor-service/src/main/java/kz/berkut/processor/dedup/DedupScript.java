package kz.berkut.processor.dedup;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Atomic windowed dedup claim, implemented as a Lua script so GET + EXPIRE (hysteresis refresh)
 * or SET happen under a single Redis round-trip:
 *
 *   if key exists   -> refresh TTL, return "EXISTING:{oldGroupId}"
 *   if key missing  -> SET with TTL, return "NEW:{candidateGroupId}"
 *
 * The window refreshes on every hit: a group only "closes" after silence >= window.
 * Single-string return avoids mixed-type deserialization issues with StringRedisTemplate.
 */
@Component
public class DedupScript {
    private static final String LUA = """
            local existing = redis.call('GET', KEYS[1])
            if existing then
              redis.call('EXPIRE', KEYS[1], ARGV[2])
              return 'EXISTING:' .. existing
            else
              redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
              return 'NEW:' .. ARGV[1]
            end
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<String> script;

    public DedupScript(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(LUA, String.class);
    }

    public DedupResult claim(String key, String candidateGroupId, long ttlSeconds) {
        String raw = redis.execute(script, List.of(key), candidateGroupId, String.valueOf(ttlSeconds));
        if (raw == null) {
            // Defensive: shouldn't happen (script always returns), but treat as new to make forward progress.
            return new DedupResult(candidateGroupId, true);
        }
        if (raw.startsWith("NEW:")) {
            return new DedupResult(raw.substring(4), true);
        }
        // EXISTING:
        return new DedupResult(raw.substring(9), false);
    }

    public record DedupResult(String groupId, boolean isNew) {}
}
