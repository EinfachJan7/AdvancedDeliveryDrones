package de.cb.drones.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerBlacklistRepository {
    private static final String PATH_PREFIX = "blacklists.";
    private static final String PLAYERS_SUFFIX = ".players";

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public PlayerBlacklistRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "blacklists.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Could not create blacklists.yml", e);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        migrateLegacyKeys();
    }

    private void migrateLegacyKeys() {
        if (!config.contains(PATH_PREFIX)) {
            return;
        }
        var section = config.getConfigurationSection(PATH_PREFIX);
        if (section == null) {
            return;
        }
        for (String ownerKey : section.getKeys(false)) {
            String packagesPath = PATH_PREFIX + ownerKey + ".packages";
            String playersPath = PATH_PREFIX + ownerKey + PLAYERS_SUFFIX;
            if (config.contains(packagesPath) && !config.contains(playersPath)) {
                config.set(playersPath, config.getStringList(packagesPath));
                config.set(packagesPath, null);
            }
            config.set(PATH_PREFIX + ownerKey + ".sockets", null);
        }
        save();
    }

    public List<UUID> getPlayerBlacklist(UUID ownerId) {
        return loadList(ownerId);
    }

    public boolean isPlayerBlacklisted(UUID ownerId, UUID senderId) {
        return getPlayerBlacklist(ownerId).contains(senderId);
    }

    public boolean addToPlayerBlacklist(UUID ownerId, UUID blockedId) {
        return addToList(ownerId, blockedId);
    }

    public boolean removeFromPlayerBlacklist(UUID ownerId, UUID blockedId) {
        return removeFromList(ownerId, blockedId);
    }

    private List<UUID> loadList(UUID ownerId) {
        List<String> raw = config.getStringList(PATH_PREFIX + ownerId + PLAYERS_SUFFIX);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> result = new ArrayList<>();
        for (String entry : raw) {
            try {
                result.add(UUID.fromString(entry));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in player blacklist for " + ownerId + ": " + entry);
            }
        }
        return result;
    }

    private boolean addToList(UUID ownerId, UUID blockedId) {
        List<UUID> current = new ArrayList<>(loadList(ownerId));
        if (current.contains(blockedId)) {
            return false;
        }
        current.add(blockedId);
        saveList(ownerId, current);
        return true;
    }

    private boolean removeFromList(UUID ownerId, UUID blockedId) {
        List<UUID> current = new ArrayList<>(loadList(ownerId));
        if (!current.remove(blockedId)) {
            return false;
        }
        saveList(ownerId, current);
        return true;
    }

    private void saveList(UUID ownerId, List<UUID> entries) {
        List<String> serialized = entries.stream().map(UUID::toString).toList();
        config.set(PATH_PREFIX + ownerId + PLAYERS_SUFFIX, serialized);
        save();
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save blacklists.yml: " + e.getMessage());
        }
    }
}
