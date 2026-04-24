package kz.berkut.processor.notifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.berkut.common.VehicleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Stateless POSTer for critical events. Keeps retry logic in one place so the listener
 * can stay thin. JDK HttpClient (not WebClient/RestClient) keeps the dependency footprint
 * to zero beyond spring-boot-starter; processor-service does not serve HTTP of its own.
 *
 * Retry policy: exponential backoff 0.2s → 1s → 5s (4 attempts total). Idempotency-Key
 * = eventId, so retrying the same POST at the receiver is safe.
 */
@Component
public class WebhookNotifier {
    private static final Logger log = LoggerFactory.getLogger(WebhookNotifier.class);
    private static final long[] BACKOFF_MS = {200L, 1000L, 5000L};

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String webhookUrl;
    private final boolean enabled;

    public WebhookNotifier(ObjectMapper mapper, @Value("${webhook.url:}") String webhookUrl) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.mapper = mapper;
        this.webhookUrl = webhookUrl;
        this.enabled = webhookUrl != null && !webhookUrl.isBlank();
        if (!enabled) {
            log.warn("webhook.url not set — CRITICAL events will be logged but not POSTed");
        } else {
            log.info("Webhook endpoint: {}", webhookUrl);
        }
    }

    public void post(VehicleEvent event) {
        if (!enabled) {
            log.info("CRITICAL (no-webhook) device={} type={} eventId={}",
                    event.deviceId(), event.type(), event.eventId());
            return;
        }

        String body;
        try {
            body = mapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Skipping webhook — failed to serialize event {}: {}", event.eventId(), e.toString());
            return;
        }

        for (int attempt = 0; attempt <= BACKOFF_MS.length; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", event.eventId())
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    log.info("webhook ok event={} status={} attempt={}", event.eventId(), resp.statusCode(), attempt);
                    return;
                }
                log.warn("webhook non-2xx status={} event={} attempt={}",
                        resp.statusCode(), event.eventId(), attempt);
            } catch (Exception e) {
                log.warn("webhook attempt={} failed event={}: {}", attempt, event.eventId(), e.toString());
            }
            if (attempt < BACKOFF_MS.length) {
                try {
                    Thread.sleep(BACKOFF_MS[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("webhook gave up after {} attempts event={}", BACKOFF_MS.length + 1, event.eventId());
    }
}
