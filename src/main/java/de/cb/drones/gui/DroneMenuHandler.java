package de.cb.drones.gui;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.command.DroneCommand;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.drone.GuiItem;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
    // private final SocketBlacklistRepository blacklistRepository;
    
    // Track pending socket renames (player UUID -> old socket name)
    private final Map<UUID, String> pendingRenames = new HashMap<>();
    // Store original blocks to restore after sign edit
    private final Map<UUID, Location> signLocations = new HashMap<>();
    private final Map<UUID, Material> signOriginalMaterials = new HashMap<>();

    public DroneMenuHandler(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, PlayerSettingsRepository settingsRepository, DroneSettings droneSettings, SocketRepository socketRepository /*, SocketBlacklistRepository blacklistRepository */) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.settingsRepository = settingsRepository;
        this.droneSettings = droneSettings;
        this.socketRepository = socketRepository;
        // this.blacklistRepository = blacklistRepository;
        this.guiItemKey = new NamespacedKey(plugin, "gui_item");
        this.menuGUI = new DroneMenuGUI(plugin, droneManager, settingsRepository, droneSettings, socketRepository, /* blacklistRepository, */ this.guiItemKey);
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
            default -> {
                if (menuType.startsWith("socket_edit:")) {
                    String socketName = menuType.substring("socket_edit:".length());
                    handleSocketEditClick(player, clicked, event.getSlot(), socketName);
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
            menuGUI.openSocketManagementMenu(player);
        }
    }

    private void handlePlayerSelectionClick(Player player, ItemStack clicked, int slot) {
        // Check for back button first with null check
        GuiItem backItem = droneSettings.guiConfig().playerSelection().items().get("back");
        if (backItem != null && slot == backItem.position()) {
            menuGUI.openMainMenu(player);
            return;
        }

        if (clicked.getType() != org.bukkit.Material.PLAYER_HEAD) return;

        org.bukkit.inventory.meta.ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String targetName = meta.getDisplayName();
        org.bukkit.entity.Player target = player.getServer().getPlayer(targetName);

        if (target != null && target.isOnline()) {
            player.closeInventory();
            // Execute the send command
            player.performCommand("drone send " + targetName);
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
            menuGUI.openPlayerSelectionMenu(player);
            return;
        }

        if (socketItem != null && slot == socketItem.position()) {
            menuGUI.openSocketSelectionMenu(player);
            return;
        }
    }

    private void handleSocketSelectionClick(Player player, ItemStack clicked, int slot) {
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
            NamespacedKey socketNameKey = new NamespacedKey("advanced-delivery-drones", "socket_name");
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
            NamespacedKey socketNameKey = new NamespacedKey("advanced-delivery-drones", "socket_name");
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

    private void handleSocketEditClick(Player player, ItemStack clicked, int slot, String socketName) {
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

            socketRepository.removeSocket(player.getUniqueId(), socketName);
            socketRepository.addSocket(player.getUniqueId(), player.getName(), socketName, player.getLocation());
            droneManager.sendMessage(player, "socket-relocated", "<name>", socketName);
            menuGUI.openSocketManagementMenu(player);
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
            droneManager.sendMessage(player, "socket-error", "<error>", "Kein Name eingegeben");
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
        
        // Perform the rename
        de.cb.drones.socket.DeliverySocket socket = socketRepository.getSocket(playerId, oldName);
        if (socket != null) {
            Location loc = socket.location();
            socketRepository.removeSocket(playerId, oldName);
            socketRepository.addSocket(playerId, player.getName(), newName, loc);
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
