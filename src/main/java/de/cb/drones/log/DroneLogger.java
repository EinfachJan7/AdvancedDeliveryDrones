package de.cb.drones.log;

public interface DroneLogger {
    void log(DroneLogEntry entry);
    void close();
}
