package de.cb.drones.config;

import de.cb.drones.drone.DeliveryDrone;
import de.cb.drones.drone.DroneManager;

import java.util.Collection;

public interface DronePersistence {
    void saveDrones(Collection<DeliveryDrone> drones);
    void loadDrones(DroneManager droneManager);
}
