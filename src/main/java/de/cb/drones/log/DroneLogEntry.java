package de.cb.drones.log;

import java.util.UUID;

public record DroneLogEntry(
        UUID id,
        UUID senderId,
        String senderName,
        UUID receiverId,
        String receiverName,
        long timestamp,
        String itemsSummary,
        String action
) {
    public DroneLogEntry {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
