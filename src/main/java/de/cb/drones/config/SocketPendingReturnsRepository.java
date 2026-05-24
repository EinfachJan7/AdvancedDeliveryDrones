package de.cb.drones.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SocketPendingReturnsRepository {
    private static final String PATH_PREFIX = "returns.";

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public SocketPendingReturnsRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "socket-pending-returns.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Could not create socket-pending-returns.yml", e);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
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
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save socket-pending-returns.yml: " + e.getMessage());
        }
    }
}
