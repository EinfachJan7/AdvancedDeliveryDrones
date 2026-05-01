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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

public class DroneMenuHandler implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    
    private final AdvancedDeliveryDronesPlugin plugin;
    private final DroneManager droneManager;
    private final PlayerSettingsRepository settingsRepository;
    private DroneSettings droneSettings;
    private final DroneMenuGUI menuGUI;
    private final SocketRepository socketRepository;
    private final NamespacedKey guiItemKey;

    public DroneMenuHandler(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, PlayerSettingsRepository settingsRepository, DroneSettings droneSettings, SocketRepository socketRepository) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.settingsRepository = settingsRepository;
        this.droneSettings = droneSettings;
        this.socketRepository = socketRepository;
        this.guiItemKey = new NamespacedKey(plugin, "gui_item");
        this.menuGUI = new DroneMenuGUI(plugin, droneManager, settingsRepository, droneSettings, socketRepository, this.guiItemKey);
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
        GuiItem sendItem = droneSettings.mainMenu().items().get("send");
        GuiItem toggleItem = droneSettings.mainMenu().items().get("toggle");
        GuiItem declineItem = droneSettings.mainMenu().items().get("decline");
        GuiItem previewItem = droneSettings.mainMenu().items().get("preview");

        if (sendItem != null && slot == sendItem.position()) { // Send Drone
            menuGUI.openTargetSelectionMenu(player);
        } else if (toggleItem != null && slot == toggleItem.position()) { // Toggle
            boolean current = settingsRepository.canReceive(player.getUniqueId());
            settingsRepository.setCanReceive(player.getUniqueId(), !current);
            player.sendMessage(droneManager.message(current ? "toggle-off" : "toggle-on", null, null));
            player.closeInventory();
            menuGUI.openMainMenu(player); // Refresh menu
        } else if (declineItem != null && slot == declineItem.position()) { // Decline
            int declined = droneManager.declineIncoming(player);
            if (declined <= 0) {
                player.sendMessage(droneManager.message("decline-none", null, null));
            } else {
                player.sendMessage(droneManager.message("decline-success", "<count>", String.valueOf(declined)));
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
        }
    }

    private void handlePlayerSelectionClick(Player player, ItemStack clicked, int slot) {
        // Check for back button first with null check
        GuiItem backItem = droneSettings.playerSelection().items().get("back");
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
        GuiItem backItem = droneSettings.targetSelection().items().get("back");
        GuiItem playerItem = droneSettings.targetSelection().items().get("player");
        GuiItem socketItem = droneSettings.targetSelection().items().get("socket");

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
        GuiItem backItem = droneSettings.socketSelection().items().get("back");

        if (backItem != null && slot == backItem.position()) {
            menuGUI.openTargetSelectionMenu(player);
            return;
        }

        if (clicked.getType() != org.bukkit.Material.BEACON) return;

        org.bukkit.inventory.meta.ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String socketName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        player.closeInventory();
        // Execute the socket send command
        player.performCommand("drone socket send " + socketName);
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        // Optional: Handle cleanup if needed
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
