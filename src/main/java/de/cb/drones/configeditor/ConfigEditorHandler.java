package de.cb.drones.configeditor;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public final class ConfigEditorHandler implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final AdvancedDeliveryDronesPlugin plugin;
    private final ConfigEditorService service;
    private final ConfigEditorGUI gui;
    private final Map<UUID, PendingChatInput> pendingChatInputs = new ConcurrentHashMap<>();
    private final Set<UUID> playersWithChanges = new CopyOnWriteArraySet<>();

    public ConfigEditorHandler(AdvancedDeliveryDronesPlugin plugin, ConfigEditorService service, ConfigEditorGUI gui) {
        this.plugin = plugin;
        this.service = service;
        this.gui = gui;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openEditor(Player player) {
        pendingChatInputs.remove(player.getUniqueId());
        gui.openCategories(player);
    }

    public void reloadGuiSettings(ConfigEditorGuiSettings settings) {
        gui.reloadSettings(settings);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ConfigEditorHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        String navAction = meta.getPersistentDataContainer().get(gui.navActionKey(), PersistentDataType.STRING);
        if (navAction != null) {
            handleNavigation(player, holder, navAction);
            return;
        }

        if (holder.type() == ConfigEditorHolder.Type.CATEGORIES) {
            String categoryId = meta.getPersistentDataContainer().get(gui.categoryIdKey(), PersistentDataType.STRING);
            if (categoryId != null) {
                gui.openOptions(player, categoryId, 0);
            }
            return;
        }

        String optionId = meta.getPersistentDataContainer().get(gui.optionIdKey(), PersistentDataType.STRING);
        if (optionId == null) {
            return;
        }

        ConfigEditorRegistry.option(optionId).ifPresent(option -> handleOptionClick(player, holder, option));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ConfigEditorHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingChatInput pending = pendingChatInputs.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());

        Bukkit.getScheduler().runTask(plugin, () -> processChatInput(player, pending, input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingChatInputs.remove(uuid);
        playersWithChanges.remove(uuid);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!playersWithChanges.contains(uuid)) {
            return;
        }
        
        // Verzögere die Prüfung um 1 Tick, um zu sehen ob ein neues Inventory geöffnet wird
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                playersWithChanges.remove(uuid);
                return;
            }
            
            // Prüfe ob der Spieler immer noch ein ConfigEditor Inventory offen hat
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ConfigEditorHolder) {
                // Noch offen, nicht reloaden
                return;
            }
            
            // GUI wurde wirklich geschlossen, jetzt reloaden
            playersWithChanges.remove(uuid);
            service.reloadRuntime();
            
            // Sende Reload-Nachricht
            String prefix = plugin.getLanguageManager().getString("prefix", "");
            String message = plugin.getLanguageManager().getString("config-editor-reloaded", "<green>Config reloaded.</green>");
            player.sendMessage(MINI_MESSAGE.deserialize(prefix + message));
        });
    }

    private void handleNavigation(Player player, ConfigEditorHolder holder, String navAction) {
        switch (navAction) {
            case "back" -> {
                if (holder.type() == ConfigEditorHolder.Type.OPTIONS) {
                    gui.openCategories(player, 0);
                } else {
                    player.closeInventory();
                }
            }
            case "previous" -> {
                if (holder.type() == ConfigEditorHolder.Type.CATEGORIES) {
                    gui.openCategories(player, holder.page() - 1);
                } else {
                    gui.openOptions(player, holder.categoryId(), holder.page() - 1);
                }
            }
            case "next" -> {
                if (holder.type() == ConfigEditorHolder.Type.CATEGORIES) {
                    gui.openCategories(player, holder.page() + 1);
                } else {
                    gui.openOptions(player, holder.categoryId(), holder.page() + 1);
                }
            }
            default -> {
            }
        }
    }

    private void handleOptionClick(Player player, ConfigEditorHolder holder, ConfigOption option) {
        switch (option.type()) {
            case BOOLEAN -> {
                if (service.toggleBoolean(option)) {
                    playersWithChanges.add(player.getUniqueId());
                    sendOptionValueMessage(player, "config-editor-boolean-toggled", option);
                }
                gui.openOptions(player, holder.categoryId(), holder.page());
            }
            case ENUM -> {
                if (service.cycleEnum(option)) {
                    playersWithChanges.add(player.getUniqueId());
                    sendOptionValueMessage(player, "config-editor-enum-cycled", option);
                }
                gui.openOptions(player, holder.categoryId(), holder.page());
            }
            default -> {
                player.closeInventory();
                pendingChatInputs.put(player.getUniqueId(), new PendingChatInput(holder.categoryId(), holder.page(), option.id()));
                sendOptionValueMessage(player, "config-editor-chat-prompt", option);
                player.sendMessage(plugin.component("config-editor-chat-cancel-hint"));
            }
        }
    }

    private void processChatInput(Player player, PendingChatInput pending, String input) {
        if (!player.isOnline()) {
            return;
        }

        ConfigOption option = ConfigEditorRegistry.option(pending.optionId()).orElse(null);
        if (option == null) {
            gui.openOptions(player, pending.categoryId(), pending.page());
            return;
        }

        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("abbrechen")) {
            player.sendMessage(plugin.component("config-editor-chat-cancelled"));
            gui.openOptions(player, pending.categoryId(), pending.page());
            return;
        }

        if (service.applyValue(option, input)) {
            playersWithChanges.add(player.getUniqueId());
            sendOptionValueMessage(player, "config-editor-value-updated", option);
        } else {
            player.sendMessage(plugin.component("config-editor-value-invalid"));
        }

        gui.openOptions(player, pending.categoryId(), pending.page());
    }

    private void sendOptionValueMessage(Player player, String key, ConfigOption option) {
        String prefix = plugin.getLanguageManager().getString("prefix", "");
        String body = plugin.getLanguageManager().getString(key, key)
                .replace("<option>", gui.localizedOptionName(option))
                .replace("<value>", service.getDisplayValue(option));
        player.sendMessage(MINI_MESSAGE.deserialize(prefix + body));
    }

    private record PendingChatInput(String categoryId, int page, String optionId) {
    }
}
