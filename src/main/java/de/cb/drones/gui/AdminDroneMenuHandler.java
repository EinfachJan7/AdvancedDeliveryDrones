package de.cb.drones.gui;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.drone.DeliveryDrone;
import de.cb.drones.drone.DroneManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;

public class AdminDroneMenuHandler implements Listener {
    private final AdvancedDeliveryDronesPlugin plugin;
    private final DroneManager droneManager;
    private final AdminDroneMenuGUI gui;
    private final FileConfiguration guiConfig;
    private final NamespacedKey droneIdKey;

    public AdminDroneMenuHandler(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, AdminDroneMenuGUI gui, FileConfiguration guiConfig) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.gui = gui;
        this.guiConfig = guiConfig;
        this.droneIdKey = new NamespacedKey(plugin, "drone_id");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        
        String titleStr = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        String expectedTitle = PlainTextComponentSerializer.plainText().serialize(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        guiConfig.getString("admin-menu.title", "<!italic><dark_red>🛡 <red>ᴀᴅᴍɪɴ ᴄᴏɴᴛʀᴏʟ</red>")
                )
        );

        if (!titleStr.equals(expectedTitle)) {
            return;
        }

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getItemMeta() == null) return;

        if (item.getItemMeta().getPersistentDataContainer().has(droneIdKey, PersistentDataType.STRING)) {
            String uuidStr = item.getItemMeta().getPersistentDataContainer().get(droneIdKey, PersistentDataType.STRING);
            if (uuidStr != null) {
                try {
                    UUID droneId = UUID.fromString(uuidStr);
                    DeliveryDrone drone = droneManager.findByDroneId(droneId);
                    if (drone != null) {
                        int cId = drone.getCancelId();
                        if (cId <= 0) {
                            java.util.List<DeliveryDrone> outgoing = droneManager.getOutgoingDrones(drone.senderId());
                            cId = outgoing.stream().mapToInt(DeliveryDrone::getCancelId).filter(id -> id > 0).max().orElse(0) + 1;
                            drone.setCancelId(cId);
                        }

                        if (event.isShiftClick() && event.isRightClick()) {
                            droneManager.destroyDrone(drone, false);
                            player.sendMessage(plugin.component("admin-drone-deleted"));
                            Player senderPlayer = Bukkit.getPlayer(drone.senderId());
                            if (senderPlayer != null) {
                                senderPlayer.sendMessage(plugin.componentMessage("admin-drone-deleted-notify", "<id>", String.valueOf(cId)));
                            }
                        } else if (event.isRightClick()) {
                            droneManager.cancelSpecific(Bukkit.getPlayer(drone.senderId()), drone);
                            player.sendMessage(plugin.component("admin-drone-recalled"));
                            Player senderPlayer = Bukkit.getPlayer(drone.senderId());
                            if (senderPlayer != null) {
                                senderPlayer.sendMessage(plugin.componentMessage("admin-drone-recalled-notify", "<id>", String.valueOf(cId)));
                            }
                        } else if (event.isLeftClick()) {
                            player.teleport(drone.currentLocation());
                            player.sendMessage(plugin.component("admin-drone-teleported"));
                        }
                    }
                    gui.open(player, 0); // Refresh
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
