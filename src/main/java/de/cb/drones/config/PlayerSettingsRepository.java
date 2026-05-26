package de.cb.drones.config;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import de.cb.drones.AdvancedDeliveryDronesPlugin;

public final class PlayerSettingsRepository {
    private static final String PATH_PREFIX = "delivery-settings.";
    private static final String RECEIVE_SUFFIX = ".can-receive";
    private static final String LAST_SEND_SUFFIX = ".last-send-time";

    private final AdvancedDeliveryDronesPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public PlayerSettingsRepository(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        reload();
    }

    public void reload() {
        if ("MYSQL".equalsIgnoreCase(plugin.getConfig().getString("database.type", "YAML")) && plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
            String data = plugin.getDatabaseManager().loadConfig("drone_player_settings");
            this.config = new YamlConfiguration();
            if (data != null) {
                try {
                    this.config.loadFromString(data);
                } catch (Exception e) {
                    plugin.getLogger().severe("Could not parse player_settings from MySQL!");
                }
            }
            if (file.exists()) {
                file.delete();
            }
        } else {
            if (!file.exists()) {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                } catch (IOException e) {
                    throw new IllegalStateException("Could not create players.yml", e);
                }
            }
            this.config = YamlConfiguration.loadConfiguration(file);
        }
    }

    public boolean canReceive(UUID playerId) {
        return config.getBoolean(PATH_PREFIX + playerId + RECEIVE_SUFFIX, true);
    }

    public void setCanReceive(UUID playerId, boolean state) {
        config.set(PATH_PREFIX + playerId + RECEIVE_SUFFIX, state);
        save();
    }

    public long getLastSendTime(UUID playerId) {
        return config.getLong(PATH_PREFIX + playerId + LAST_SEND_SUFFIX, 0);
    }

    public void setLastSendTime(UUID playerId) {
        config.set(PATH_PREFIX + playerId + LAST_SEND_SUFFIX, System.currentTimeMillis());
        save();
    }

    public long getRemainingCooldown(UUID playerId, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0;
        }
        long lastSendTime = getLastSendTime(playerId);
        if (lastSendTime == 0) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - lastSendTime) / 1000;
        long remaining = cooldownSeconds - elapsed;
        return remaining > 0 ? remaining : 0;
    }

    public boolean canPlayerSend(UUID playerId, int cooldownSeconds) {
        return getRemainingCooldown(playerId, cooldownSeconds) == 0;
    }

    private void save() {
        if ("MYSQL".equalsIgnoreCase(plugin.getConfig().getString("database.type", "YAML")) && plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
            plugin.getDatabaseManager().saveConfig("drone_player_settings", config.saveToString());
        } else {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save players.yml: " + e.getMessage());
            }
        }
    }
}
