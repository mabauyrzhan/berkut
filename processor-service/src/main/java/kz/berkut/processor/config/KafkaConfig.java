package kz.berkut.processor.config;

import kz.berkut.common.GpsPoint;
import kz.berkut.common.Vehicle;
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
 * One factory per logical consumer — each with its own stable group.id. This makes
 * offsets resumable across restarts and lets any listener be scaled/moved independently.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrap;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, GpsPoint> gpsBatchFactory() {
        return batchJsonFactory(GpsPoint.class, "processor-gps-ingest", 3);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, VehicleEvent> rawEventBatchFactory() {
        return batchJsonFactory(VehicleEvent.class, "processor-dedup", 3);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Vehicle> vehicleRegistryFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Vehicle> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(jsonConsumerFactory(Vehicle.class, "processor-vehicle-registry"));
        factory.setConcurrency(1);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, VehicleEvent> notifierFactory() {
        // Single-message listener so retry/backoff semantics are clear per event.
        ConcurrentKafkaListenerContainerFactory<String, VehicleEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(jsonConsumerFactory(VehicleEvent.class, "processor-notifier"));
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    private <T> ConsumerFactory<String, T> jsonConsumerFactory(Class<T> type, String groupId) {
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(type);
        deserializer.setUseTypeHeaders(false);
        deserializer.addTrustedPackages("kz.berkut.common");

        Map<String, Object> configs = new HashMap<>();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
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
