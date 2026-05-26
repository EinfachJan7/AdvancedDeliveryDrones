package de.cb.drones.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemStack;
import de.cb.drones.AdvancedDeliveryDronesPlugin;

public final class SocketPendingReturnsRepository {
    private static final String PATH_PREFIX = "returns.";

    private final AdvancedDeliveryDronesPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public SocketPendingReturnsRepository(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "socket-pending-returns.yml");
        reload();
    }

    public void reload() {
        if ("MYSQL".equalsIgnoreCase(plugin.getConfig().getString("database.type", "YAML")) && plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
            String data = plugin.getDatabaseManager().loadConfig("socket_pending_returns");
            this.config = new YamlConfiguration();
            if (data != null) {
                try {
                    this.config.loadFromString(data);
                } catch (Exception e) {
                    plugin.getLogger().severe("Could not parse socket_pending_returns from MySQL!");
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
                    throw new IllegalStateException("Could not create socket_pending_returns.yml", e);
                }
            }
            this.config = YamlConfiguration.loadConfiguration(file);
        }
    }

    public void addReturns(UUID ownerId, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<ItemStack> merged = new ArrayList<>(loadReturns(ownerId));
        for (ItemStack stack : items) {
            if (stack != null && !stack.getType().isAir()) {
                merged.add(stack.clone());
            }
        }
        saveReturns(ownerId, merged);
    }

    public List<ItemStack> takeReturns(UUID ownerId) {
        List<ItemStack> items = loadReturns(ownerId);
        if (items.isEmpty()) {
            return List.of();
        }
        config.set(PATH_PREFIX + ownerId, null);
        save();
        return items;
    }

    private List<ItemStack> loadReturns(UUID ownerId) {
        List<?> raw = config.getList(PATH_PREFIX + ownerId);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<ItemStack> result = new ArrayList<>();
        for (Object entry : raw) {
            if (entry instanceof ItemStack stack && !stack.getType().isAir()) {
                result.add(stack.clone());
            }
        }
        return result;
    }

    private void saveReturns(UUID ownerId, List<ItemStack> items) {
        config.set(PATH_PREFIX + ownerId, items);
        save();
    }

    private void save() {
        if ("MYSQL".equalsIgnoreCase(plugin.getConfig().getString("database.type", "YAML")) && plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isConnected()) {
            plugin.getDatabaseManager().saveConfig("socket_pending_returns", config.saveToString());
        } else {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save socket_pending_returns.yml: " + e.getMessage());
            }
        }
    }
}
