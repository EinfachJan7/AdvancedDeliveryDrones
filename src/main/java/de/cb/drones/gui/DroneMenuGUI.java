package de.cb.drones.gui;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.drone.GuiSettings;
import de.cb.drones.drone.GuiItem;
import de.cb.drones.socket.DeliverySocket;
import de.cb.drones.socket.SocketRepository;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DroneMenuGUI {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final AdvancedDeliveryDronesPlugin plugin;
    private final DroneManager droneManager;
    private final PlayerSettingsRepository settingsRepository;
    private DroneSettings droneSettings;
    private final SocketRepository socketRepository;
    private final NamespacedKey guiItemKey;

    public DroneMenuGUI(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, PlayerSettingsRepository settingsRepository, DroneSettings droneSettings, SocketRepository socketRepository, NamespacedKey guiItemKey) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.settingsRepository = settingsRepository;
        this.droneSettings = droneSettings;
        this.socketRepository = socketRepository;
        this.guiItemKey = guiItemKey;
    }

    public void updateSettings(DroneSettings newSettings) {
        this.droneSettings = newSettings;
    }
    
    public void openMainMenu(Player player) {
        GuiSettings menuSettings = droneSettings.mainMenu();
        DroneMenuHandler.DroneMenuHolder holder = new DroneMenuHandler.DroneMenuHolder("main_menu");
        Inventory menu = player.getServer().createInventory(holder, menuSettings.size(), 
            MINI_MESSAGE.deserialize(menuSettings.title()));
        holder.setInventory(menu);
        
        // First, fill all slots with fill item if configured
        if (menuSettings.fillItem() != null) {
            ItemStack filler = createMenuItem(menuSettings.fillItem().material(), 
                menuSettings.fillItem().name(), menuSettings.fillItem().lore());
            for (int i = 0; i < menuSettings.size(); i++) {
                menu.setItem(i, filler);
            }
        }
        
        // Then add configurable menu items (this will override fill items)
        for (Map.Entry<String, GuiItem> entry : menuSettings.items().entrySet()) {
            GuiItem item = entry.getValue();
            if (item.position() >= 0 && item.position() < menuSettings.size()) {
                ItemStack menuItem = createMenuItem(item.material(), item.name(), item.lore());
                menu.setItem(item.position(), menuItem);
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
        DroneMenuHandler.DroneMenuHolder holder = new DroneMenuHandler.DroneMenuHolder("player_selection");
        Inventory menu = sender.getServer().createInventory(holder, size, 
            MINI_MESSAGE.deserialize(menuSettings.title()));
        holder.setInventory(menu);
        
        // First, fill all slots with fill item if configured
        if (menuSettings.fillItem() != null) {
            ItemStack filler = createMenuItem(menuSettings.fillItem().material(), 
                menuSettings.fillItem().name(), menuSettings.fillItem().lore());
            for (int i = 0; i < size; i++) {
                menu.setItem(i, filler);
            }
        }
        
        // Add player heads (this will override fill items)
        for (int i = 0; i < onlinePlayers.size() && i < size; i++) {
            org.bukkit.entity.Player target = onlinePlayers.get(i);
            ItemStack headItem = createPlayerHead(target);
            menu.setItem(i, headItem);
        }
        
        // Add back button if configured (this will override fill items and player heads if conflict)
        GuiItem backItem = menuSettings.items().get("back");
        if (backItem != null && backItem.position() >= 0 && backItem.position() < size) {
            ItemStack backButton = createMenuItem(backItem.material(), backItem.name(), backItem.lore());
            menu.setItem(backItem.position(), backButton);
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
            
            // Mark this item as a GUI item to prevent manipulation
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(guiItemKey, PersistentDataType.BYTE, (byte) 1);
            
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

    public void openTargetSelectionMenu(Player player) {
        GuiSettings menuSettings = droneSettings.targetSelection();
        DroneMenuHandler.DroneMenuHolder holder = new DroneMenuHandler.DroneMenuHolder("target_selection");
        Inventory menu = player.getServer().createInventory(holder, menuSettings.size(),
            MINI_MESSAGE.deserialize(menuSettings.title()));
        holder.setInventory(menu);

        // First, fill all slots with fill item if configured
        if (menuSettings.fillItem() != null) {
            ItemStack filler = createMenuItem(menuSettings.fillItem().material(),
                menuSettings.fillItem().name(), menuSettings.fillItem().lore());
            for (int i = 0; i < menuSettings.size(); i++) {
                menu.setItem(i, filler);
            }
        }

        // Then add configurable menu items (this will override fill items)
        for (Map.Entry<String, GuiItem> entry : menuSettings.items().entrySet()) {
            GuiItem item = entry.getValue();
            if (item.position() >= 0 && item.position() < menuSettings.size()) {
                ItemStack menuItem = createMenuItem(item.material(), item.name(), item.lore());
                menu.setItem(item.position(), menuItem);
            }
        }

        player.openInventory(menu);
    }

    public void openSocketSelectionMenu(Player player) {
        GuiSettings menuSettings = droneSettings.socketSelection();
        List<DeliverySocket> allSockets = socketRepository.getAllSockets();
        int size = Math.max(menuSettings.size(), Math.min(54, ((allSockets.size() + 8) / 9) * 9));

        DroneMenuHandler.DroneMenuHolder holder = new DroneMenuHandler.DroneMenuHolder("socket_selection");
        Inventory menu = player.getServer().createInventory(holder, size,
            MINI_MESSAGE.deserialize(menuSettings.title()));
        holder.setInventory(menu);

        // First, fill all slots with fill item if configured
        if (menuSettings.fillItem() != null) {
            ItemStack filler = createMenuItem(menuSettings.fillItem().material(),
                menuSettings.fillItem().name(), menuSettings.fillItem().lore());
            for (int i = 0; i < size; i++) {
                menu.setItem(i, filler);
            }
        }

        // Add socket items (this will override fill items)
        for (int i = 0; i < allSockets.size() && i < size; i++) {
            DeliverySocket socket = allSockets.get(i);
            ItemStack socketItem = createSocketItem(socket);
            menu.setItem(i, socketItem);
        }

        // Add back button if configured (this will override fill items and socket items if conflict)
        GuiItem backItem = menuSettings.items().get("back");
        if (backItem != null && backItem.position() >= 0 && backItem.position() < size) {
            ItemStack backButton = createMenuItem(backItem.material(), backItem.name(), backItem.lore());
            menu.setItem(backItem.position(), backButton);
        }

        player.openInventory(menu);
    }

    private ItemStack createSocketItem(DeliverySocket socket) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Use configurable format for socket name
            String nameFormat = droneSettings.socketItemNameFormat()
                .replace("<name>", socket.name());
            meta.displayName(MINI_MESSAGE.deserialize(nameFormat));

            // Use configurable formats for lore
            String ownerFormat = droneSettings.socketItemOwnerFormat()
                .replace("<owner>", socket.ownerName());
            String emptyLine = droneSettings.socketItemEmptyLine();
            String clickHint = droneSettings.socketItemClickHint();

            List<Component> lore = List.of(
                MINI_MESSAGE.deserialize(ownerFormat),
                MINI_MESSAGE.deserialize(emptyLine),
                MINI_MESSAGE.deserialize(clickHint)
            );
            meta.lore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }
}
