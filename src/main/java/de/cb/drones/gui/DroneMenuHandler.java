package de.cb.drones.gui;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.command.DroneCommand;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class DroneMenuHandler implements Listener {
    private final AdvancedDeliveryDronesPlugin plugin;
    private final DroneManager droneManager;
    private final PlayerSettingsRepository settingsRepository;
    private final DroneSettings droneSettings;
    private final DroneMenuGUI menuGUI;
    
    public DroneMenuHandler(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, PlayerSettingsRepository settingsRepository, DroneSettings droneSettings) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.settingsRepository = settingsRepository;
        this.droneSettings = droneSettings;
        this.menuGUI = new DroneMenuGUI(plugin, droneManager, settingsRepository, droneSettings);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    public DroneMenuGUI getMenuGUI() {
        return menuGUI;
    }
    
    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().getTitle();
        if (!title.equals("Drone Menü") && !title.equals("Spieler auswählen")) return;
        
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        
        if (title.equals("Drone Menü")) {
            handleMainMenuClick(player, clicked, event.getSlot());
        } else if (title.equals("Spieler auswählen")) {
            handlePlayerSelectionClick(player, clicked, event.getSlot());
        }
    }
    
    private void handleMainMenuClick(Player player, ItemStack clicked, int slot) {
        // Get the configured positions from GUI settings
        int sendPos = droneSettings.mainMenu().items().get("send").position();
        int togglePos = droneSettings.mainMenu().items().get("toggle").position();
        int declinePos = droneSettings.mainMenu().items().get("decline").position();
        int previewPos = droneSettings.mainMenu().items().get("preview").position();
        
        if (slot == sendPos) { // Send Drone
            menuGUI.openPlayerSelectionMenu(player);
        } else if (slot == togglePos) { // Toggle
            boolean current = settingsRepository.canReceive(player.getUniqueId());
            settingsRepository.setCanReceive(player.getUniqueId(), !current);
            player.sendMessage(droneManager.message(current ? "toggle-off" : "toggle-on", null, null));
            player.closeInventory();
            menuGUI.openMainMenu(player); // Refresh menu
        } else if (slot == declinePos) { // Decline
            int declined = droneManager.declineIncoming(player);
            if (declined <= 0) {
                player.sendMessage(droneManager.message("decline-none", null, null));
            } else {
                player.sendMessage(droneManager.message("decline-success", "<count>", String.valueOf(declined)));
            }
            player.closeInventory();
        } else if (slot == previewPos) { // Preview (if available)
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
        // Check for back button first
        if (droneSettings.playerSelection().items().containsKey("back")) {
            int backPos = droneSettings.playerSelection().items().get("back").position();
            if (slot == backPos) {
                menuGUI.openMainMenu(player);
                return;
            }
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
    
    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        // Optional: Handle cleanup if needed
    }
    
    public static record DroneMenuHolder() implements InventoryHolder {
        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return null;
        }
    }
}
