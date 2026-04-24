package kz.berkut.processor.ingest;

import kz.berkut.common.Topics;
import kz.berkut.common.Vehicle;
import kz.berkut.processor.storage.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class VehicleRegistryListener {
    private static final Logger log = LoggerFactory.getLogger(VehicleRegistryListener.class);

    private final VehicleRepository repo;

    public VehicleRegistryListener(VehicleRepository repo) {
        this.repo = repo;
    }

    @KafkaListener(topics = Topics.VEHICLES_REGISTRY, containerFactory = "vehicleRegistryFactory")
    public void onVehicle(Vehicle vehicle) {
        repo.upsert(vehicle);
        log.debug("Registered vehicle {} plate={}", vehicle.deviceId(), vehicle.licensePlate());
    }
}
