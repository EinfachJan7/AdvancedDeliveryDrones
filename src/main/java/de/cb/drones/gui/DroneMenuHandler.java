package de.cb.drones.gui;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.config.PlayerBlacklistRepository;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.drone.GuiItem;
import de.cb.drones.drone.GuiSettings;
import de.cb.drones.socket.SocketRepository;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DroneMenuHandler implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    
    private final AdvancedDeliveryDronesPlugin plugin;
    private final DroneManager droneManager;
    private final PlayerSettingsRepository settingsRepository;
    private DroneSettings droneSettings;
    private final DroneMenuGUI menuGUI;
    private final SocketRepository socketRepository;
    private final NamespacedKey guiItemKey;
    private final PlayerBlacklistRepository blacklistRepository;
    
    // Track pending socket renames (player UUID -> old socket name)
    private final Map<UUID, String> pendingRenames = new HashMap<>();
    // Store original blocks to restore after sign edit
    private final Map<UUID, Location> signLocations = new HashMap<>();
    private final Map<UUID, Material> signOriginalMaterials = new HashMap<>();

    public DroneMenuHandler(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, PlayerSettingsRepository settingsRepository, PlayerBlacklistRepository blacklistRepository, DroneSettings droneSettings, SocketRepository socketRepository) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.settingsRepository = settingsRepository;
        this.blacklistRepository = blacklistRepository;
        this.droneSettings = droneSettings;
        this.socketRepository = socketRepository;
        this.guiItemKey = new NamespacedKey(plugin, "gui_item");
        this.menuGUI = new DroneMenuGUI(plugin, droneManager, settingsRepository, blacklistRepository, droneSettings, socketRepository, this.guiItemKey);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public DroneMenuGUI getMenuGUI() {
        return menuGUI;
    }

    public void updateSettings(DroneSettings newSettings) {
        this.droneSettings = newSettings;
        this.menuGUI.updateSettings(newSettings);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if this is our GUI by checking the InventoryHolder
        if (!(event.getInventory().getHolder() instanceof DroneMenuHolder)) return;

        // Cancel ALL interactions to prevent item removal
        event.setCancelled(true);
        
        // Prevent any inventory manipulation
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY ||
            event.getAction() == org.bukkit.event.inventory.InventoryAction.HOTBAR_MOVE_AND_READD ||
            event.getAction() == org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP ||
            event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR ||
            event.getAction() == org.bukkit.event.inventory.InventoryAction.UNKNOWN) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // Get menu type from holder - completely dynamic, no hardcoded strings
        DroneMenuHolder holder = (DroneMenuHolder) event.getInventory().getHolder();
        String menuType = holder.getMenuType();
        
        // Handle clicks based on menu type
        switch (menuType) {
            case "main_menu" -> handleMainMenuClick(player, clicked, event.getSlot());
            case "player_selection" -> handlePlayerSelectionClick(player, clicked, event.getSlot());
            case "target_selection" -> handleTargetSelectionClick(player, clicked, event.getSlot());
            case "socket_selection" -> handleSocketSelectionClick(player, clicked, event.getSlot());
            case "socket_management" -> handleSocketManagementClick(player, clicked, event.getSlot(), event.isRightClick());
            case "blacklist_management" -> handleBlacklistManagementClick(player, clicked, event.getSlot());
            default -> {
                if (menuType.startsWith("socket_edit:")) {
                    String socketName = menuType.substring("socket_edit:".length());
                    handleSocketEditClick(player, clicked, event.getSlot(), socketName);
                } else if (menuType.startsWith("trust_selection:")) {
                    String[] parts = menuType.substring("trust_selection:".length()).split(":");
                    String socketName = parts[0];
                    boolean isTrust = Boolean.parseBoolean(parts[1]);
                    handleTrustSelectionClick(player, clicked, event.getSlot(), socketName, isTrust);
                } else if (menuType.startsWith("blacklist_selection:")) {
                    String[] parts = menuType.substring("blacklist_selection:".length()).split(":", 3);
                    if ("player".equals(parts[0]) && parts.length >= 2) {
                        handlePlayerBlacklistSelectionClick(player, clicked, event.getSlot(), Boolean.parseBoolean(parts[1]));
                    } else if ("socket".equals(parts[0]) && parts.length >= 3) {
                        handleSocketBlacklistSelectionClick(player, clicked, event.getSlot(), parts[1], Boolean.parseBoolean(parts[2]));
                    }
                } else if (menuType.startsWith("socket_trust_menu:")) {
                    String socketName = menuType.substring("socket_trust_menu:".length());
                    handleSocketTrustMenuClick(player, clicked, event.getSlot(), socketName);
                } else if (menuType.startsWith("socket_blacklist_menu:")) {
                    String socketName = menuType.substring("socket_blacklist_menu:".length());
                    handleSocketBlacklistMenuClick(player, clicked, event.getSlot(), socketName);
                }
            }
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if this is our GUI by checking the InventoryHolder
        if (!(event.getInventory().getHolder() instanceof DroneMenuHolder)) return;

        // Cancel ALL drag operations to prevent item manipulation
        event.setCancelled(true);
    }

    private void handleMainMenuClick(Player player, ItemStack clicked, int slot) {
        // Get the configured positions from GUI settings with null checks
        GuiItem sendItem = droneSettings.guiConfig().mainMenu().items().get("send");
        GuiItem toggleItem = droneSettings.guiConfig().mainMenu().items().get("toggle");
        GuiItem declineItem = droneSettings.guiConfig().mainMenu().items().get("decline");
        GuiItem previewItem = droneSettings.guiConfig().mainMenu().items().get("preview");
        GuiItem socketManageItem = droneSettings.guiConfig().mainMenu().items().get("socket-manage");
        GuiItem blacklistItem = droneSettings.guiConfig().mainMenu().items().get("blacklist");

        if (sendItem != null && slot == sendItem.position()) { // Send Drone
            menuGUI.openTargetSelectionMenu(player);
        } else if (toggleItem != null && slot == toggleItem.position()) { // Toggle
            boolean current = settingsRepository.canReceive(player.getUniqueId());
            settingsRepository.setCanReceive(player.getUniqueId(), !current);
            droneManager.sendMessage(player, current ? "toggle-off" : "toggle-on");
            player.closeInventory();
            menuGUI.openMainMenu(player); // Refresh menu
        } else if (declineItem != null && slot == declineItem.position()) { // Decline
            int declined = droneManager.declineIncoming(player);
            if (declined <= 0) {
                droneManager.sendMessage(player, "decline-none");
            } else {
                droneManager.sendMessage(player, "decline-success", "<count>", String.valueOf(declined));
            }
            player.closeInventory();
        } else if (previewItem != null && slot == previewItem.position()) { // Preview (if available)
            // Find first incoming drone and open preview
            droneManager.activeDronesSnapshot().stream()
                .filter(drone -> drone.receiverId().equals(player.getUniqueId()))
                .findFirst()
                .ifPresent(drone -> {
                    player.closeInventory();
                    player.performCommand("drone preview " + drone.droneId());
                });
        } else if (socketManageItem != null && slot == socketManageItem.position()) { // Socket Management
            if (!droneSettings.socketsEnabled()) {
                droneManager.sendMessage(player, "sockets-disabled");
                return;
            }
            menuGUI.openSocketManagementMenu(player);
        } else if (blacklistItem != null && slot == blacklistItem.position()) {
            menuGUI.openBlacklistManagementMenu(player);
        }
    }

    private void handleBlacklistManagementClick(Player player, ItemStack clicked, int slot) {
        GuiItem backItem = droneSettings.guiConfig().blacklistManagement().items().get("back");
        if (backItem != null && slot == backItem.position()) {
            menuGUI.openMainMenu(player);
            return;
        }

        GuiItem playerAdd = droneSettings.guiConfig().blacklistManagement().items().get("player-add");
        if (playerAdd != null && slot == playerAdd.position()) {
            menuGUI.openPlayerBlacklistSelectionMenu(player, true);
            return;
        }

        GuiItem playerRemove = droneSettings.guiConfig().blacklistManagement().items().get("player-remove");
        if (playerRemove != null && slot == playerRemove.position()) {
            menuGUI.openPlayerBlacklistSelectionMenu(player, false);
        }
    }

    private void handlePlayerBlacklistSelectionClick(Player player, ItemStack clicked, int slot, boolean isAdd) {
        GuiSettings selectionSettings = isAdd
                ? droneSettings.guiConfig().blacklistPlayerAddSelection()
                : droneSettings.guiConfig().blacklistPlayerRemoveSelection();
        GuiItem backItem = selectionSettings.items().get("back");
        if (backItem != null && slot == backItem.position()) {
            menuGUI.openBlacklistManagementMenu(player);
            return;
        }
        if (handleBlacklistHeadClick(player, clicked, isAdd, null, true)) {
            menuGUI.openPlayerBlacklistSelectionMenu(player, isAdd);
        }
    }

    private void handleSocketBlacklistSelectionClick(Player player, ItemStack clicked, int slot, String socketName, boolean isAdd) {
        if (!droneSettings.socketsEnabled()) {
            droneManager.sendMessage(player, "sockets-disabled");
            player.closeInventory();
            return;
        }
        GuiSettings selectionSettings = isAdd
                ? droneSettings.guiConfig().blacklistSocketAddSelection()
                : droneSettings.guiConfig().blacklistSocketRemoveSelection();
        GuiItem backItem = selectionSettings.items().get("back");
        if (backItem != null && slot == backItem.position()) {
            menuGUI.openSocketBlacklistMenu(player, socketName);
            return;
        }
        if (handleBlacklistHeadClick(player, clicked, isAdd, socketName, false)) {
            menuGUI.openSocketBlacklistSelectionMenu(player, socketName, isAdd);
        }
    }

    private boolean handleBlacklistHeadClick(Player player, ItemStack clicked, boolean isAdd, String socketName, boolean playerScope) {
        if (clicked.getType() != Material.PLAYER_HEAD) {
            return false;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return false;
        }

        UUID targetUUID = resolveTargetUuid(meta);
        if (targetUUID == null) {
            return false;
        }

        if (isAdd) {
            org.bukkit.OfflinePlayer target = player.getServer().getOfflinePlayer(targetUUID);
            if (target.getUniqueId().equals(player.getUniqueId())) {
                return false;
            }

            String targetName = target.getName() != null ? target.getName() : targetUUID.toString();

            boolean added = playerScope
                    ? blacklistRepository.addToPlayerBlacklist(player.getUniqueId(), targetUUID)
                    : socketRepository.addBlacklistedPlayer(player.getUniqueId(), socketName, targetUUID);

            if (added) {
                if (playerScope) {
                    droneManager.sendMessage(player, "blacklist-player-added", "<player>", targetName);
                } else {
                    droneManager.sendMessage(player, "blacklist-socket-added", "<player>", targetName, "<socket>", socketName);
                }
            } else if (playerScope) {
                droneManager.sendMessage(player, "blacklist-player-already", "<player>", targetName);
            } else {
                droneManager.sendMessage(player, "blacklist-socket-already", "<player>", targetName, "<socket>", socketName);
            }
            return true;
        }

        boolean removed = playerScope
                ? blacklistRepository.removeFromPlayerBlacklist(player.getUniqueId(), targetUUID)
                : socketRepository.removeBlacklistedPlayer(player.getUniqueId(), socketName, targetUUID);

        String targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
        if (targetName == null) {
            targetName = targetUUID.toString();
        }

        if (removed) {
            if (playerScope) {
                droneManager.sendMessage(player, "blacklist-player-removed", "<player>", targetName);
            } else {
                droneManager.sendMessage(player, "blacklist-socket-removed", "<player>", targetName, "<socket>", socketName);
            }
        } else if (playerScope) {
            droneManager.sendMessage(player, "blacklist-player-not-found", "<player>", targetName);
        } else {
            droneManager.sendMessage(player, "blacklist-socket-not-found", "<player>", targetName, "<socket>", socketName);
        }
        return true;
    }

    private UUID resolveTargetUuid(ItemMeta meta) {
        NamespacedKey playerUuidKey = new NamespacedKey(plugin, "player_uuid");
        if (meta.getPersistentDataContainer().has(playerUuidKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            String uuidString = meta.getPersistentDataContainer().get(playerUuidKey, org.bukkit.persistence.PersistentDataType.STRING);
            if (uuidString != null) {
                try {
                    return UUID.fromString(uuidString);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        String targetName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        Player target = Bukkit.getPlayer(targetName);
        if (target != null) {
            return target.getUniqueId();
        }
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getUniqueId();
        }
        return null;
    }

    private void handlePlayerSelectionClick(Player player, ItemStack clicked, int slot) {
        if (!droneSettings.playersEnabled()) {
            droneManager.sendMessage(player, "players-disabled");
            player.closeInventory();
            return;
        }
        // Check for back button first with null check
        GuiItem backItem = droneSettings.guiConfig().playerSelection().items().get("back");
        if (backItem != null && slot == backItem.position()) {
            menuGUI.openMainMenu(player);
            return;
        }

        if (clicked.getType() != org.bukkit.Material.PLAYER_HEAD) return;

        org.bukkit.inventory.meta.ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        UUID targetUUID = null;
        NamespacedKey playerUuidKey = new NamespacedKey(plugin, "player_uuid");
        if (meta.getPersistentDataContainer().has(playerUuidKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            String uuidString = meta.getPersistentDataContainer().get(playerUuidKey, org.bukkit.persistence.PersistentDataType.STRING);
            if (uuidString != null) {
                try {
                    targetUUID = UUID.fromString(uuidString);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        org.bukkit.entity.Player target = null;
        if (targetUUID != null) {
            target = player.getServer().getPlayer(targetUUID);
        }

        if (target == null) {
            String targetName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            target = player.getServer().getPlayer(targetName);
        }

        if (target != null && target.isOnline()) {
            player.closeInventory();
            // Execute the send command
            player.performCommand("drone send " + target.getName());
        }
    }

    private void handleTargetSelectionClick(Player player, ItemStack clicked, int slot) {
        // Get the configured positions from GUI settings with null checks
        GuiItem backItem = droneSettings.guiConfig().targetSelection().items().get("back");
        GuiItem playerItem = droneSettings.guiConfig().targetSelection().items().get("player");
        GuiItem socketItem = droneSettings.guiConfig().targetSelection().items().get("socket");

        if (backItem != null && slot == backItem.position()) {
            menuGUI.openMainMenu(player);
            return;
        }

        if (playerItem != null && slot == playerItem.position()) {
            if (!droneSettings.playersEnabled()) {
                droneManager.sendMessage(player, "players-disabled");
                return;
            }
            menuGUI.openPlayerSelectionMenu(player);
            return;
        }

        if (socketItem != null && slot == socketItem.position()) {
            if (!droneSettings.socketsEnabled()) {
                droneManager.sendMessage(player, "sockets-disabled");
                return;
            }
            menuGUI.openSocketSelectionMenu(player);
            return;
        }
    }

    private void handleSocketSelectionClick(Player player, ItemStack clicked, int slot) {
        if (!droneSettings.socketsEnabled()) {
            droneManager.sendMessage(player, "sockets-disabled");
            player.closeInventory();
            return;
        }
        // Get the configured back button position with null check
        GuiItem backItem = droneSettings.guiConfig().socketSelection().items().get("back");

        if (backItem != null && slot == backItem.position()) {
            menuGUI.openTargetSelectionMenu(player);
            return;
        }

        if (clicked.getType() != droneSettings.guiConfig().socketItemMaterial()) return;

        org.bukkit.inventory.meta.ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        // Try to get socket name from persistent data first
        String socketName = null;
        if (meta.getPersistentDataContainer() != null) {
            NamespacedKey socketNameKey = new NamespacedKey(plugin, "socket_name");
            socketName = meta.getPersistentDataContainer().get(socketNameKey, org.bukkit.persistence.PersistentDataType.STRING);
        }
        
        // Fallback to display name if persistent data is not available
        if (socketName == null) {
            socketName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        
        player.closeInventory();
        // Execute the socket send command
        player.performCommand("drone socket send " + socketName);
    }

    private void handleSocketManagementClick(Player player, ItemStack clicked, int slot, boolean isRightClick) {
        if (!droneSettings.socketsEnabled()) {
            droneManager.sendMessage(player, "sockets-disabled");
            player.closeInventory();
            return;
        }
        // Check for back button first
        GuiItem backItem = droneSettings.guiConfig().socketManagement().items().get("back");
        if (backItem != null && slot == backItem.position()) {
            menuGUI.openMainMenu(player);
            return;
        }

        if (clicked.getType() != droneSettings.guiConfig().socketManagementItemMaterial()) return;

        org.bukkit.inventory.meta.ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        // Get socket name from persistent data
        String socketName = null;
        if (meta.getPersistentDataContainer() != null) {
            NamespacedKey socketNameKey = new NamespacedKey(plugin, "socket_name");
            socketName = meta.getPersistentDataContainer().get(socketNameKey, org.bukkit.persistence.PersistentDataType.STRING);
        }

        if (socketName == null) return;

        // Right click to delete, left click to edit
        if (isRightClick) {
            boolean removed = socketRepository.removeSocket(player.getUniqueId(), socketName);
            if (removed) {
                droneManager.sendMessage(player, "socket-deleted", "<name>", socketName);
            } else {
                droneManager.sendMessage(player, "socket-not-found");
            }
            menuGUI.openSocketManagementMenu(player);
        } else {
            // Left click - open edit menu
            menuGUI.openSocketEditMenu(player, socketName);
        }
    }

    private void handleTrustSelectionClick(Player player, ItemStack clicked, int slot, String socketName, boolean isTrust) {
        if (!droneSettings.socketsEnabled()) {
            droneManager.sendMessage(player, "sockets-disabled");
            player.closeInventory();
            return;
        }
        GuiSettings selectionSettings = isTrust
                ? droneSettings.guiConfig().trustPlayerSelection()
                : droneSettings.guiConfig().untrustPlayerSelection();
        GuiItem backItem = selectionSettings.items().get("back");
        if (backItem != null && slot == backItem.position()) {
            menuGUI.openSocketTrustMenu(player, socketName);
            return;
        }

        if (clicked.getType() != org.bukkit.Material.PLAYER_HEAD) return;

        org.bukkit.inventory.meta.ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        if (isTrust) {
            String targetName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            UUID targetUUID = resolveTargetUuid(meta);

            if (targetUUID != null) {
                if (socketRepository.addTrustedPlayer(player.getUniqueId(), socketName, targetUUID)) {
                    droneManager.sendMessage(player, "socket-trust-added", "<socket>", socketName, "<player>", targetName);
                } else {
                    droneManager.sendMessage(player, "socket-trust-already", "<socket>", socketName, "<player>", targetName);
                }
            }
        } else {
            UUID targetUUID = resolveTargetUuid(meta);

            if (targetUUID != null) {
                if (socketRepository.removeTrustedPlayer(player.getUniqueId(), socketName, targetUUID)) {
                    // Try to get player name for message
                    org.bukkit.OfflinePlayer targetPlayer = org.bukkit.Bukkit.getOfflinePlayer(targetUUID);
                    String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
                    droneManager.sendMessage(player, "socket-trust-removed", "<socket>", socketName, "<player>", targetName);
                } else {
                    org.bukkit.OfflinePlayer targetPlayer = org.bukkit.Bukkit.getOfflinePlayer(targetUUID);
                    String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";
                    droneManager.sendMessage(player, "socket-trust-not-found", "<socket>", socketName, "<player>", targetName);
                }
            }
        }

        menuGUI.openTrustPlayerSelectionMenu(player, socketName, isTrust);
    }

    private void handleSocketTrustMenuClick(Player player, ItemStack clicked, int slot, String socketName) {
        if (!droneSettings.socketsEnabled()) {
            droneManager.sendMessage(player, "sockets-disabled");
            player.closeInventory();
            return;
        }
        GuiSettings menuSettings = droneSettings.guiConfig().socketTrustMenu();
        GuiItem backItem = menuSettings.items().get("back");
        if (backItem != null && slot == backItem.position()) {
            menuGUI.openSocketEditMenu(player, socketName);
            return;
        }

        GuiItem trustItem = menuSettings.items().get("trust");
        if (trustItem != null && slot == trustItem.position()) {
            menuGUI.openTrustPlayerSelectionMenu(player, socketName, true);
            return;
        }

        GuiItem untrustItem = menuSettings.items().get("untrust");
        if (untrustItem != null && slot == untrustItem.position()) {
            menuGUI.openTrustPlayerSelectionMenu(player, socketName, false);
        }
    }

    private void handleSocketBlacklistMenuClick(Player player, ItemStack clicked, int slot, String socketName) {
        if (!droneSettings.socketsEnabled()) {
            droneManager.sendMessage(player, "sockets-disabled");
            player.closeInventory();
            return;
        }
        GuiSettings menuSettings = droneSettings.guiConfig().socketBlacklistMenu();
        GuiItem backItem = menuSettings.items().get("back");
        if (backItem != null && slot == backItem.position()) {
            menuGUI.openSocketEditMenu(player, socketName);
            return;
        }

        GuiItem blacklistAddItem = menuSettings.items().get("blacklist-add");
        if (blacklistAddItem != null && slot == blacklistAddItem.position()) {
            menuGUI.openSocketBlacklistSelectionMenu(player, socketName, true);
            return;
        }

        GuiItem blacklistRemoveItem = menuSettings.items().get("blacklist-remove");
        if (blacklistRemoveItem != null && slot == blacklistRemoveItem.position()) {
            menuGUI.openSocketBlacklistSelectionMenu(player, socketName, false);
        }
    }

    private void handleSocketEditClick(Player player, ItemStack clicked, int slot, String socketName) {
        if (!droneSettings.socketsEnabled()) {
            droneManager.sendMessage(player, "sockets-disabled");
            player.closeInventory();
            return;
        }
        GuiItem backItem = droneSettings.guiConfig().socketEdit().items().get("back");
        if (backItem != null && slot == backItem.position()) {
            menuGUI.openSocketManagementMenu(player);
            return;
        }

        GuiItem renameItem = droneSettings.guiConfig().socketEdit().items().get("rename");
        if (renameItem != null && slot == renameItem.position()) {
            player.closeInventory();
            openSignRename(player, socketName);
            return;
        }

        GuiItem relocateItem = droneSettings.guiConfig().socketEdit().items().get("relocate");
        if (relocateItem != null && slot == relocateItem.position()) {
            de.cb.drones.socket.DeliverySocket socket = socketRepository.getSocket(player.getUniqueId(), socketName);
            if (socket == null) {
                droneManager.sendMessage(player, "socket-not-found", "<name>", socketName);
                menuGUI.openSocketManagementMenu(player);
                return;
            }

            if (droneManager.isDroneFlyingToSocket(socketName)) {
                droneManager.sendMessage(player, "socket-drone-incoming", "<name>", socketName);
                menuGUI.openSocketManagementMenu(player);
                return;
            }

            socketRepository.relocateSocket(player.getUniqueId(), socketName, player.getLocation());
            droneManager.sendMessage(player, "socket-relocated", "<name>", socketName);
            menuGUI.openSocketManagementMenu(player);
            return;
        }

        GuiItem trustManagementItem = droneSettings.guiConfig().socketEdit().items().get("trust-management");
        if (trustManagementItem != null && slot == trustManagementItem.position()) {
            menuGUI.openSocketTrustMenu(player, socketName);
            return;
        }

        GuiItem blacklistManagementItem = droneSettings.guiConfig().socketEdit().items().get("blacklist-management");
        if (blacklistManagementItem != null && slot == blacklistManagementItem.position()) {
            menuGUI.openSocketBlacklistMenu(player, socketName);
            return;
        }

        GuiItem deleteItem = droneSettings.guiConfig().socketEdit().items().get("delete");
        if (deleteItem != null && slot == deleteItem.position()) {
            boolean removed = socketRepository.removeSocket(player.getUniqueId(), socketName);
            if (removed) {
                droneManager.sendMessage(player, "socket-deleted", "<name>", socketName);
            } else {
                droneManager.sendMessage(player, "socket-not-found");
            }
            menuGUI.openSocketManagementMenu(player);
            return;
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        // Optional: Handle cleanup if needed
    }

    /**
     * Opens a sign editor for the player to rename a socket.
     * Creates a temporary sign at the player's location for editing.
     */
    private void openSignRename(Player player, String socketName) {
        // Store the pending rename
        pendingRenames.put(player.getUniqueId(), socketName);
        
        // Get a location for the sign (2 blocks above player so it's not visible)
        Location signLoc = player.getLocation().clone().add(0, 2, 0);
        Block block = signLoc.getBlock();
        
        // Store original block state
        signLocations.put(player.getUniqueId(), signLoc);
        signOriginalMaterials.put(player.getUniqueId(), block.getType());
        
        // Place a temporary sign
        block.setType(Material.OAK_SIGN);
        
        if (block.getState() instanceof Sign sign) {
            // Set placeholder text using configurable strings
            sign.getSide(Side.FRONT).setLine(0, droneSettings.guiConfig().signRenameBorderLine());
            sign.getSide(Side.FRONT).setLine(1, droneSettings.guiConfig().signRenameTitleLine());
            sign.getSide(Side.FRONT).setLine(2, socketName);
            sign.getSide(Side.FRONT).setLine(3, droneSettings.guiConfig().signRenameBorderLine());
            sign.update();
            
            // Open sign editor for player
            player.openSign(sign);
        }
    }
    
    /**
     * Handles sign change events for socket renaming.
     */
    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Check if this player has a pending rename
        if (!pendingRenames.containsKey(playerId)) {
            return;
        }
        
        // Get the old socket name
        String oldName = pendingRenames.remove(playerId);
        
        // Get the new name from the sign (combine all lines and clean up)
        StringBuilder newNameBuilder = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            String line = event.getLine(i);
            if (line != null && !line.isBlank() && 
                !line.contains(droneSettings.guiConfig().signRenameBorderLine()) && !line.equals(droneSettings.guiConfig().signRenameTitleLine())) {
                newNameBuilder.append(line.trim());
            }
        }
        String newName = newNameBuilder.toString().trim();
        
        // Cancel the sign change to prevent it from persisting
        event.setCancelled(true);
        
        // Restore the original block
        Location signLoc = signLocations.remove(playerId);
        Material originalMaterial = signOriginalMaterials.remove(playerId);
        if (signLoc != null && originalMaterial != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                signLoc.getBlock().setType(originalMaterial);
            });
        }
        
        // Validate the new name
        if (newName.isBlank()) {
            droneManager.sendMessage(player, "socket-error", "<error>", plugin.getLanguageManager().getString("socket-error-no-name"));
            return;
        }
        
        if (newName.length() > 32) {
            droneManager.sendMessage(player, "socket-name-too-long");
            return;
        }
        
        // Check if socket exists
        if (socketRepository.getSocket(playerId, oldName) == null) {
            droneManager.sendMessage(player, "socket-not-found", "<name>", oldName);
            return;
        }
        
        // Check if new name already exists
        if (socketRepository.socketNameExists(playerId, newName)) {
            droneManager.sendMessage(player, "socket-exists", "<name>", newName);
            return;
        }
        
        if (socketRepository.renameSocket(playerId, oldName, newName)) {
            droneManager.sendMessage(player, "socket-renamed", "<old>", oldName, "<new>", newName);
        }
    }

    public static class DroneMenuHolder implements InventoryHolder {
        private final String menuType;
        private org.bukkit.inventory.Inventory inventory;
        
        public DroneMenuHolder(String menuType) {
            this.menuType = menuType;
        }
        
        public String getMenuType() {
            return menuType;
        }
        
        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return inventory;
        }
        
        public void setInventory(org.bukkit.inventory.Inventory inventory) {
            this.inventory = inventory;
        }
    }
}
