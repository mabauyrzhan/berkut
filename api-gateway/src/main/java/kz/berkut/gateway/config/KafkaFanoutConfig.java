package kz.berkut.gateway.config;

import kz.berkut.common.GpsPoint;
import kz.berkut.common.VehicleEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Each api-gateway instance uses a unique consumer group (UUID generated at startup via
 * ${random.uuid}). That makes every instance a full consumer of the topic — it receives
 * every message and pushes to its own WebSocket clients. No Redis Pub/Sub or sticky
 * sessions needed. Duplicates trafic per extra instance, but for <50 instances the cost
 * is fine; described in README scaling section.
 */
@Configuration
public class KafkaFanoutConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrap;

    @Value("${fanout.group-id-gps}")
    private String gpsGroupId;

    @Value("${fanout.group-id-events}")
    private String eventsGroupId;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, GpsPoint> gpsFanoutFactory() {
        return batchJsonFactory(GpsPoint.class, gpsGroupId, 2);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, VehicleEvent> eventsFanoutFactory() {
        return batchJsonFactory(VehicleEvent.class, eventsGroupId, 2);
    }

    private <T> ConsumerFactory<String, T> jsonConsumerFactory(Class<T> type, String groupId) {
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(type);
        deserializer.setUseTypeHeaders(false);
        deserializer.addTrustedPackages("kz.berkut.common");

        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // "latest" for fanout: a freshly-started gateway should not replay history.
        // History is served via REST backfill (/api/events?since=...).
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configs.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        return new DefaultKafkaConsumerFactory<>(configs, new StringDeserializer(), deserializer);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> batchJsonFactory(
            Class<T> type, String groupId, int concurrency) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(jsonConsumerFactory(type, groupId));
        factory.setBatchListener(true);
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        return factory;
    }
}
