package de.cb.drones.log;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class YamlDroneLogger implements DroneLogger {
    private final AdvancedDeliveryDronesPlugin plugin;
    private final File logFile;
    private YamlConfiguration logConfig;

    public YamlDroneLogger(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "logs.yml");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create logs.yml", e);
            }
        }
        this.logConfig = YamlConfiguration.loadConfiguration(logFile);
    }

    @Override
    public void log(DroneLogEntry entry) {
        List<Map<?, ?>> logs = logConfig.getMapList("logs");
        logs.add(Map.of(
                "id", entry.id().toString(),
                "sender_id", entry.senderId().toString(),
                "sender_name", entry.senderName() != null ? entry.senderName() : "",
                "receiver_id", entry.receiverId().toString(),
                "receiver_name", entry.receiverName() != null ? entry.receiverName() : "",
                "timestamp", entry.timestamp(),
                "items", entry.itemsSummary() != null ? entry.itemsSummary() : "",
                "action", entry.action() != null ? entry.action() : ""
        ));
        logConfig.set("logs", logs);
        saveAsync();
    }

    private void saveAsync() {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                logConfig.save(logFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save logs.yml", e);
            }
        });
    }

    @Override
    public void close() {
        // Nothing to close
    }
}
