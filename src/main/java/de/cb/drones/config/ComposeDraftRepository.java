package de.cb.drones.config;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Persists compose-hub package contents and send context across server restarts.
 */
public final class ComposeDraftRepository {
    private static final String ROOT = "drafts.";

    private final AdvancedDeliveryDronesPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public ComposeDraftRepository(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "compose-drafts.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Could not create compose-drafts.yml", e);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public Optional<StoredComposeDraft> load(UUID senderId) {
        String path = ROOT + senderId;
        if (!config.contains(path)) {
            return Optional.empty();
        }
        UUID receiverId = UUID.fromString(config.getString(path + ".receiverId"));
        Location fixedTarget = config.getLocation(path + ".fixedTarget");
        boolean adminSend = config.getBoolean(path + ".adminSend");
        boolean exactSocketTarget = config.getBoolean(path + ".exactSocketTarget");
        String socketName = config.getString(path + ".socketName");
        boolean animalsOnlyMode = config.getBoolean(path + ".animalsOnlyMode");
        List<UUID> animalIds = config.getStringList(path + ".selectedAnimalIds").stream()
                .map(UUID::fromString)
                .toList();
        ItemStack[] contents = readInventory(path + ".inventory");
        return Optional.of(new StoredComposeDraft(
                receiverId,
                fixedTarget,
                adminSend,
                exactSocketTarget,
                socketName,
                animalIds,
                animalsOnlyMode,
                contents
        ));
    }

    public void save(UUID senderId, StoredComposeDraft draft) {
        if (draft == null) {
            delete(senderId);
            return;
        }
        String path = ROOT + senderId;
        config.set(path + ".receiverId", draft.receiverId().toString());
        config.set(path + ".fixedTarget", draft.fixedTarget());
        config.set(path + ".adminSend", draft.adminSend());
        config.set(path + ".exactSocketTarget", draft.exactSocketTarget());
        config.set(path + ".socketName", draft.socketName());
        config.set(path + ".animalsOnlyMode", draft.animalsOnlyMode());
        config.set(path + ".selectedAnimalIds", draft.selectedAnimalIds().stream().map(UUID::toString).toList());
        config.set(path + ".inventory", draft.contents());
        save();
    }

    public void delete(UUID senderId) {
        config.set(ROOT + senderId, null);
        save();
    }

    public Map<UUID, StoredComposeDraft> loadAll() {
        ConfigurationSection section = config.getConfigurationSection("drafts");
        if (section == null) {
            return Map.of();
        }
        Map<UUID, StoredComposeDraft> result = new HashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                UUID senderId = UUID.fromString(key);
                load(senderId).ifPresent(draft -> result.put(senderId, draft));
            } catch (IllegalArgumentException ignored) {
                // skip invalid keys
            }
        }
        return result;
    }

    private ItemStack[] readInventory(String path) {
        List<?> raw = config.getList(path);
        if (raw == null || raw.isEmpty()) {
            return new ItemStack[0];
        }
        ItemStack[] items = new ItemStack[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            Object entry = raw.get(i);
            items[i] = entry instanceof ItemStack stack ? stack : null;
        }
        return items;
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save compose-drafts.yml: " + e.getMessage());
        }
    }

    public record StoredComposeDraft(
            UUID receiverId,
            Location fixedTarget,
            boolean adminSend,
            boolean exactSocketTarget,
            String socketName,
            List<UUID> selectedAnimalIds,
            boolean animalsOnlyMode,
            ItemStack[] contents
    ) {
        public StoredComposeDraft {
            selectedAnimalIds = selectedAnimalIds == null ? List.of() : List.copyOf(selectedAnimalIds);
            contents = contents == null ? new ItemStack[0] : contents;
        }

        public List<ItemStack> nonEmptyItems() {
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack stack : contents) {
                if (stack != null && !stack.getType().isAir()) {
                    items.add(stack.clone());
                }
            }
            return items;
        }
    }
}
