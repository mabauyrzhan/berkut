package kz.berkut.simulator.fleet;

import kz.berkut.common.Vehicle;
import kz.berkut.simulator.config.SimulatorProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VehicleFactory {

    public List<SimulatedVehicle> create(SimulatorProperties props) {
        Random rand = new Random(props.fleet().seed());
        PlateGenerator plates = new PlateGenerator(rand);

        double centerLat = props.bbox().centerLat();
        double centerLon = props.bbox().centerLon();
        double rLat = props.bbox().radiusKm() / 111.0;
        double rLon = rLat / Math.cos(Math.toRadians(centerLat));

        long gpsIntervalMs = props.gps().intervalSeconds() * 1000L;
        List<SimulatedVehicle> fleet = new ArrayList<>(props.fleet().size());

        for (int i = 0; i < props.fleet().size(); i++) {
            Vehicle meta = new Vehicle(
                    String.format("veh-%04d", i + 1),
                    plates.next(),
                    DriverNames.random(rand)
            );
            double lat = centerLat + (rand.nextDouble() * 2 - 1) * rLat;
            double lon = centerLon + (rand.nextDouble() * 2 - 1) * rLon;
            double heading = rand.nextDouble() * 360;
            double speed = 30 + rand.nextDouble() * 50;

            // Spread initial emit times across the GPS interval so 1k vehicles don't all tick together.
            long initialOffset = (long) (rand.nextDouble() * gpsIntervalMs);

            fleet.add(new SimulatedVehicle(meta, lat, lon, heading, speed, initialOffset, rand));
        }
        return fleet;
    }
}
