package de.cb.drones.gui;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.drone.DeliveryDrone;
import de.cb.drones.drone.DroneManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;

import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;

public class AdminDroneMenuGUI {
    private final AdvancedDeliveryDronesPlugin plugin;
    private final DroneManager droneManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final FileConfiguration guiConfig;

    public AdminDroneMenuGUI(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, FileConfiguration guiConfig) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.guiConfig = guiConfig;
    }

    public void open(Player player, int page) {
        List<DeliveryDrone> activeDrones = droneManager.activeDronesSnapshot();
        
        int size = guiConfig.getInt("admin-menu.size", 54);
        String titleString = guiConfig.getString("admin-menu.title", "<!italic><dark_red>🛡 <red>ᴀᴅᴍɪɴ ᴄᴏɴᴛʀᴏʟ</red>");
        Component title = miniMessage.deserialize(titleString);
        Inventory inventory = Bukkit.createInventory(null, size, title);

        List<Integer> slots = guiConfig.getIntegerList("admin-menu.content-slots");
        if (slots.isEmpty()) {
            for (int i = 0; i < 45; i++) {
                slots.add(i);
            }
        }
        
        int itemsPerPage = slots.size();
        int totalPages = (int) Math.ceil((double) activeDrones.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int startIndex = page * itemsPerPage;
        for (int i = 0; i < itemsPerPage && startIndex + i < activeDrones.size(); i++) {
            DeliveryDrone drone = activeDrones.get(startIndex + i);
            int slot = slots.get(i);
            
            ItemStack item = new ItemStack(Material.ENDER_EYE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String nameFormat = guiConfig.getString("admin-menu.drone-item.name-format", "<!italic><gold><bold>✈ ᴅʀᴏɴᴇ #<id></bold></gold>");
                nameFormat = nameFormat.replace("<id>", drone.droneId().toString().substring(0, 8));
                meta.displayName(miniMessage.deserialize(nameFormat));

                List<String> loreFormat = guiConfig.getStringList("admin-menu.drone-item.lore");
                List<Component> lore = new java.util.ArrayList<>();
                for (String line : loreFormat) {
                    line = line.replace("<sender>", drone.senderName());
                    line = line.replace("<receiver>", drone.receiverName() != null ? drone.receiverName() : drone.socketName());
                    line = line.replace("<status>", drone.isLanded() ? "Landed" : "Flying");
                    line = line.replace("<distance>", String.valueOf(drone.distanceToTargetMeters()));
                    line = line.replace("<items>", drone.totalItemAmount() + " / " + drone.attachedAnimalCount());
                    lore.add(miniMessage.deserialize(line));
                }
                meta.lore(lore);
                // Store drone ID in PersistentDataContainer for click handling
                meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "drone_id"),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    drone.droneId().toString()
                );
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
        }

        // Add pagination
        if (page > 0) {
            int prevSlot = guiConfig.getInt("admin-menu.items.previous-page.position", 48);
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.displayName(miniMessage.deserialize(guiConfig.getString("admin-menu.items.previous-page.name", "<!italic><yellow>◀ ᴘʀᴇᴠɪᴏᴜs</yellow>")));
            prev.setItemMeta(meta);
            inventory.setItem(prevSlot, prev);
        }

        if (page < totalPages - 1) {
            int nextSlot = guiConfig.getInt("admin-menu.items.next-page.position", 50);
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.displayName(miniMessage.deserialize(guiConfig.getString("admin-menu.items.next-page.name", "<!italic><yellow>ɴᴇxᴛ ▶</yellow>")));
            next.setItemMeta(meta);
            inventory.setItem(nextSlot, next);
        }

        player.openInventory(inventory);
    }
}
