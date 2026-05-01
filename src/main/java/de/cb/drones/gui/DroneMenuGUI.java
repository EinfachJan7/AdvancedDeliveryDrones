package de.cb.drones.gui;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.drone.GuiSettings;
import de.cb.drones.drone.GuiItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DroneMenuGUI {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    
    private final AdvancedDeliveryDronesPlugin plugin;
    private final DroneManager droneManager;
    private final PlayerSettingsRepository settingsRepository;
    private final DroneSettings droneSettings;
    
    public DroneMenuGUI(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, PlayerSettingsRepository settingsRepository, DroneSettings droneSettings) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.settingsRepository = settingsRepository;
        this.droneSettings = droneSettings;
    }
    
    public void openMainMenu(Player player) {
        GuiSettings menuSettings = droneSettings.mainMenu();
        Inventory menu = player.getServer().createInventory(null, menuSettings.size(), 
            Component.text(MINI_MESSAGE.serialize(MINI_MESSAGE.deserialize(menuSettings.title()))));
        
        // Add configurable menu items
        for (Map.Entry<String, GuiItem> entry : menuSettings.items().entrySet()) {
            GuiItem item = entry.getValue();
            if (item.position() >= 0 && item.position() < menuSettings.size()) {
                ItemStack menuItem = createMenuItem(item.material(), item.name(), item.lore());
                menu.setItem(item.position(), menuItem);
            }
        }
        
        // Fill remaining slots with fill item
        if (menuSettings.fillItem() != null) {
            ItemStack filler = createMenuItem(menuSettings.fillItem().material(), 
                menuSettings.fillItem().name(), menuSettings.fillItem().lore());
            for (int i = 0; i < menuSettings.size(); i++) {
                if (menu.getItem(i) == null) {
                    menu.setItem(i, filler);
                }
            }
        }
        
        player.openInventory(menu);
    }
    
    public void openPlayerSelectionMenu(Player sender) {
        GuiSettings menuSettings = droneSettings.playerSelection();
        List<org.bukkit.entity.Player> onlinePlayers = new ArrayList<>();
        for (org.bukkit.entity.Player player : sender.getServer().getOnlinePlayers()) {
            if (settingsRepository.canReceive(player.getUniqueId()) && !player.equals(sender)) {
                onlinePlayers.add(player);
            }
        }
        
        int size = Math.max(menuSettings.size(), Math.min(54, ((onlinePlayers.size() + 8) / 9) * 9));
        Inventory menu = sender.getServer().createInventory(null, size, 
            Component.text(MINI_MESSAGE.serialize(MINI_MESSAGE.deserialize(menuSettings.title()))));
        
        // Add player heads
        for (int i = 0; i < onlinePlayers.size() && i < size; i++) {
            org.bukkit.entity.Player target = onlinePlayers.get(i);
            ItemStack headItem = createPlayerHead(target);
            menu.setItem(i, headItem);
        }
        
        // Add back button if configured
        if (menuSettings.items().containsKey("back")) {
            GuiItem backItem = menuSettings.items().get("back");
            if (backItem.position() >= 0 && backItem.position() < size) {
                ItemStack backButton = createMenuItem(backItem.material(), backItem.name(), backItem.lore());
                menu.setItem(backItem.position(), backButton);
            }
        }
        
        // Fill remaining slots with fill item
        if (menuSettings.fillItem() != null) {
            ItemStack filler = createMenuItem(menuSettings.fillItem().material(), 
                menuSettings.fillItem().name(), menuSettings.fillItem().lore());
            for (int i = 0; i < size; i++) {
                if (menu.getItem(i) == null) {
                    menu.setItem(i, filler);
                }
            }
        }
        
        sender.openInventory(menu);
    }
    
    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI_MESSAGE.deserialize(name));
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(MINI_MESSAGE.deserialize(line));
            }
            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createPlayerHead(org.bukkit.entity.Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(player.getName()));
            
            List<Component> lore = List.of(
                Component.text("Klicke um eine Drohne"),
                Component.text("an " + player.getName() + " zu senden")
            );
            meta.lore(lore);
            
            // Set player head texture
            if (meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(player);
            }
            
            head.setItemMeta(meta);
        }
        return head;
    }
}
