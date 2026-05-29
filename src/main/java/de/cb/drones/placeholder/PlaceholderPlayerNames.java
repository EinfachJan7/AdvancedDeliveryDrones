package de.cb.drones.placeholder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

final class PlaceholderPlayerNames {

    private PlaceholderPlayerNames() {
    }

    static String fromUuid(UUID uuid) {
        if (uuid == null) {
            return "";
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return offline.getUniqueId().toString();
    }

    static String fromUuidString(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            return fromUuid(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    static String joinNames(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            names.add(fromUuid(id));
        }
        return String.join(", ", names);
    }
}
