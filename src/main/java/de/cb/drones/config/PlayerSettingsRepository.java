package de.cb.drones.config;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerSettingsRepository {
    private static final String PATH_PREFIX = "delivery-settings.";

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public PlayerSettingsRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        reload();
    }

    public void reload() {
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

    public boolean canReceive(UUID playerId) {
        return config.getBoolean(PATH_PREFIX + playerId, true);
    }

    public void setCanReceive(UUID playerId, boolean state) {
        config.set(PATH_PREFIX + playerId, state);
        save();
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save players.yml: " + e.getMessage());
        }
    }
}
