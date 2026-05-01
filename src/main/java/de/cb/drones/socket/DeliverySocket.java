package de.cb.drones.socket;

import org.bukkit.Location;

import java.util.UUID;

public record DeliverySocket(
        UUID socketId,
        UUID ownerId,
        String ownerName,
        String name,
        Location location,
        long createdTimestamp
) {
    public DeliverySocket {
        if (socketId == null) {
            throw new IllegalArgumentException("socketId cannot be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId cannot be null");
        }
        if (ownerName == null || ownerName.isBlank()) {
            throw new IllegalArgumentException("ownerName cannot be null or blank");
        }
        if (location == null) {
            throw new IllegalArgumentException("location cannot be null");
        }
    }

    public static DeliverySocket create(UUID ownerId, String ownerName, String name, Location location) {
        return new DeliverySocket(
                UUID.randomUUID(),
                ownerId,
                ownerName,
                name,
                location.clone(),
                System.currentTimeMillis()
        );
    }

    public String getWorldName() {
        return location.getWorld() != null ? location.getWorld().getName() : "unknown";
    }

    public String getCoordinates() {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }
}
