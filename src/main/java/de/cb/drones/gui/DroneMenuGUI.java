package de.cb.drones.gui;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.drone.GuiSettings;
import de.cb.drones.drone.GuiItem;
import de.cb.drones.socket.DeliverySocket;
import de.cb.drones.socket.SocketRepository;
// import de.cb.drones.socket.SocketBlacklistRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
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
    // private final SocketBlacklistRepository blacklistRepository;
    private final NamespacedKey guiItemKey;

    public DroneMenuGUI(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, PlayerSettingsRepository settingsRepository, DroneSettings droneSettings, SocketRepository socketRepository /*, SocketBlacklistRepository blacklistRepository */, NamespacedKey guiItemKey) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.settingsRepository = settingsRepository;
        this.droneSettings = droneSettings;
        this.socketRepository = socketRepository;
        // this.blacklistRepository = blacklistRepository;
        this.guiItemKey = guiItemKey;
    }

    public void updateSettings(DroneSettings newSettings) {
        this.droneSettings = newSettings;
    }
    
    public void openMainMenu(Player player) {
        GuiSettings menuSettings = droneSettings.guiConfig().mainMenu();
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
        GuiSettings menuSettings = droneSettings.guiConfig().playerSelection();
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
            // Use configurable name format with placeholder replacement
            String nameFormat = droneSettings.guiConfig().playerHeadNameFormat()
                .replace("<player>", player.getName());
            meta.displayName(MINI_MESSAGE.deserialize(nameFormat));

            // Use configurable lore with placeholder replacement
            List<Component> loreComponents = new ArrayList<>();
            for (String line : droneSettings.guiConfig().playerHeadLore()) {
                String formattedLine = line.replace("<player>", player.getName());
                loreComponents.add(MINI_MESSAGE.deserialize(formattedLine));
            }
            meta.lore(loreComponents);

            // Set player head texture
            if (meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(player);
            }

            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createPlayerHeadWithUUID(org.bukkit.entity.Player player, UUID uuid) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            // Use configurable name format with placeholder replacement
            String nameFormat = droneSettings.guiConfig().playerHeadNameFormat()
                .replace("<player>", player.getName());
            meta.displayName(MINI_MESSAGE.deserialize(nameFormat));

            // Use configurable lore with placeholder replacement
            List<Component> loreComponents = new ArrayList<>();
            for (String line : droneSettings.guiConfig().playerHeadLore()) {
                String formattedLine = line.replace("<player>", player.getName());
                loreComponents.add(MINI_MESSAGE.deserialize(formattedLine));
            }
            meta.lore(loreComponents);

            // Store UUID in persistent data for offline player handling
            if (meta.getPersistentDataContainer() != null) {
                NamespacedKey playerUuidKey = new NamespacedKey("advanced-delivery-drones", "player_uuid");
                meta.getPersistentDataContainer().set(playerUuidKey, PersistentDataType.STRING, uuid.toString());
            }

            // Set player head texture
            if (meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(player);
            }

            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createTrustPlayerHead(org.bukkit.entity.Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            // Use trust-specific name format with placeholder replacement
            String nameFormat = droneSettings.guiConfig().trustPlayerHeadNameFormat()
                .replace("<player>", player.getName());
            meta.displayName(MINI_MESSAGE.deserialize(nameFormat));

            // Use trust-specific lore with placeholder replacement
            List<Component> loreComponents = new ArrayList<>();
            for (String line : droneSettings.guiConfig().trustPlayerHeadLore()) {
                String formattedLine = line.replace("<player>", player.getName());
                loreComponents.add(MINI_MESSAGE.deserialize(formattedLine));
            }
            meta.lore(loreComponents);

            // Set player head texture
            if (meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(player);
            }

            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createUntrustPlayerHeadWithUUID(org.bukkit.entity.Player player, UUID uuid) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            // Use untrust-specific name format with placeholder replacement
            String nameFormat = droneSettings.guiConfig().untrustPlayerHeadNameFormat()
                .replace("<player>", player.getName());
            meta.displayName(MINI_MESSAGE.deserialize(nameFormat));

            // Use untrust-specific lore with placeholder replacement
            List<Component> loreComponents = new ArrayList<>();
            for (String line : droneSettings.guiConfig().untrustPlayerHeadLore()) {
                String formattedLine = line.replace("<player>", player.getName());
                loreComponents.add(MINI_MESSAGE.deserialize(formattedLine));
            }
            meta.lore(loreComponents);

            // Store UUID in persistent data for offline player handling
            if (meta.getPersistentDataContainer() != null) {
                NamespacedKey playerUuidKey = new NamespacedKey("advanced-delivery-drones", "player_uuid");
                meta.getPersistentDataContainer().set(playerUuidKey, PersistentDataType.STRING, uuid.toString());
            }

            // Set player head texture
            if (meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(player);
            }

            head.setItemMeta(meta);
        }
        return head;
    }

    public void openTargetSelectionMenu(Player player) {
        GuiSettings menuSettings = droneSettings.guiConfig().targetSelection();
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
        GuiSettings menuSettings = droneSettings.guiConfig().socketSelection();
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
        ItemStack item = new ItemStack(droneSettings.guiConfig().socketItemMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Store the actual socket name in persistent data for identification
            if (meta.getPersistentDataContainer() != null) {
                NamespacedKey socketNameKey = new NamespacedKey("advanced-delivery-drones", "socket_name");
                meta.getPersistentDataContainer().set(socketNameKey, PersistentDataType.STRING, socket.name());
            }
            
            // Use configurable format for socket name
            String nameFormat = droneSettings.guiConfig().socketItemNameFormat()
                .replace("<name>", socket.name());
            meta.displayName(MINI_MESSAGE.deserialize(nameFormat));

            // Use configurable formats for lore
            String ownerFormat = droneSettings.guiConfig().socketItemOwnerFormat()
                .replace("<owner>", socket.ownerName());
            String emptyLine = droneSettings.guiConfig().socketItemEmptyLine();
            String clickHint = droneSettings.guiConfig().socketItemClickHint();

            // Build lore with trusted players
            List<Component> lore = new ArrayList<>();
            lore.add(MINI_MESSAGE.deserialize(ownerFormat));
            
            // Add trusted players if any
            if (!socket.trustedPlayers().isEmpty()) {
                for (UUID trustedUuid : socket.trustedPlayers()) {
                    org.bukkit.entity.Player trustedPlayer = org.bukkit.Bukkit.getPlayer(trustedUuid);
                    if (trustedPlayer != null) {
                        String trustedFormat = "<!italic><#9ca3af>  ᴠᴇʀᴛʀᴀᴜᴇᴛ: <#4ade80>" + trustedPlayer.getName();
                        lore.add(MINI_MESSAGE.deserialize(trustedFormat));
                    }
                }
            }
            
            lore.add(MINI_MESSAGE.deserialize(emptyLine));
            lore.add(MINI_MESSAGE.deserialize(clickHint));
            
            meta.lore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    public void openSocketManagementMenu(Player player) {
        GuiSettings menuSettings = droneSettings.guiConfig().socketManagement();
        List<DeliverySocket> playerSockets = socketRepository.getSocketsByOwner(player.getUniqueId());
        int size = Math.max(menuSettings.size(), Math.min(54, ((playerSockets.size() + 8) / 9) * 9));

        DroneMenuHandler.DroneMenuHolder holder = new DroneMenuHandler.DroneMenuHolder("socket_management");
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

        // Add socket items
        for (int i = 0; i < playerSockets.size() && i < size; i++) {
            DeliverySocket socket = playerSockets.get(i);
            ItemStack socketItem = createSocketManagementItem(socket);
            menu.setItem(i, socketItem);
        }

        // If no sockets, show "no-sockets" item
        if (playerSockets.isEmpty()) {
            GuiItem noSocketsItem = menuSettings.items().get("no-sockets-item");
            if (noSocketsItem != null && noSocketsItem.position() >= 0 && noSocketsItem.position() < size) {
                ItemStack noSocketsButton = createMenuItem(noSocketsItem.material(), noSocketsItem.name(), noSocketsItem.lore());
                menu.setItem(noSocketsItem.position(), noSocketsButton);
            }
        }

        // Add back button if configured
        GuiItem backItem = menuSettings.items().get("back");
        if (backItem != null && backItem.position() >= 0 && backItem.position() < size) {
            ItemStack backButton = createMenuItem(backItem.material(), backItem.name(), backItem.lore());
            menu.setItem(backItem.position(), backButton);
        }

        player.openInventory(menu);
    }

    private ItemStack createSocketManagementItem(DeliverySocket socket) {
        ItemStack item = new ItemStack(droneSettings.guiConfig().socketManagementItemMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Store the actual socket name in persistent data for identification
            if (meta.getPersistentDataContainer() != null) {
                NamespacedKey socketNameKey = new NamespacedKey("advanced-delivery-drones", "socket_name");
                meta.getPersistentDataContainer().set(socketNameKey, PersistentDataType.STRING, socket.name());
            }

            // Use configurable format for socket name
            String nameFormat = droneSettings.guiConfig().socketManagementItemNameFormat()
                .replace("<name>", socket.name());
            meta.displayName(MINI_MESSAGE.deserialize(nameFormat));

            // Use configurable formats for lore
            String locationFormat = droneSettings.guiConfig().socketManagementItemLocationFormat()
                .replace("<world>", socket.location().getWorld().getName())
                .replace("<x>", String.valueOf(socket.location().getBlockX()))
                .replace("<y>", String.valueOf(socket.location().getBlockY()))
                .replace("<z>", String.valueOf(socket.location().getBlockZ()));
            String emptyLine = droneSettings.guiConfig().socketManagementItemEmptyLine();
            String deleteHint = droneSettings.guiConfig().socketManagementItemDeleteHint();

            List<Component> lore = List.of(
                MINI_MESSAGE.deserialize(locationFormat),
                MINI_MESSAGE.deserialize(emptyLine),
                MINI_MESSAGE.deserialize(deleteHint)
            );
            meta.lore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    public void openSocketEditMenu(Player player, String socketName) {
        GuiSettings menuSettings = droneSettings.guiConfig().socketEdit();
        int size = menuSettings.size();

        DroneMenuHandler.DroneMenuHolder holder = new DroneMenuHandler.DroneMenuHolder("socket_edit:" + socketName);
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

        // Add action items
        GuiItem renameItem = menuSettings.items().get("rename");
        if (renameItem != null && renameItem.position() >= 0 && renameItem.position() < size) {
            ItemStack renameButton = createMenuItem(renameItem.material(), renameItem.name(), renameItem.lore());
            menu.setItem(renameItem.position(), renameButton);
        }

        GuiItem relocateItem = menuSettings.items().get("relocate");
        if (relocateItem != null && relocateItem.position() >= 0 && relocateItem.position() < size) {
            ItemStack relocateButton = createMenuItem(relocateItem.material(), relocateItem.name(), relocateItem.lore());
            menu.setItem(relocateItem.position(), relocateButton);
        }

        GuiItem trustItem = menuSettings.items().get("trust");
        if (trustItem != null && trustItem.position() >= 0 && trustItem.position() < size) {
            ItemStack trustButton = createMenuItem(trustItem.material(), trustItem.name(), trustItem.lore());
            menu.setItem(trustItem.position(), trustButton);
        }

        GuiItem untrustItem = menuSettings.items().get("untrust");
        if (untrustItem != null && untrustItem.position() >= 0 && untrustItem.position() < size) {
            ItemStack untrustButton = createMenuItem(untrustItem.material(), untrustItem.name(), untrustItem.lore());
            menu.setItem(untrustItem.position(), untrustButton);
        }

        GuiItem deleteItem = menuSettings.items().get("delete");
        if (deleteItem != null && deleteItem.position() >= 0 && deleteItem.position() < size) {
            ItemStack deleteButton = createMenuItem(deleteItem.material(), deleteItem.name(), deleteItem.lore());
            menu.setItem(deleteItem.position(), deleteButton);
        }

        // Add back button
        GuiItem backItem = menuSettings.items().get("back");
        if (backItem != null && backItem.position() >= 0 && backItem.position() < size) {
            ItemStack backButton = createMenuItem(backItem.material(), backItem.name(), backItem.lore());
            menu.setItem(backItem.position(), backButton);
        }

        player.openInventory(menu);
    }

    public void openTrustPlayerSelectionMenu(Player player, String socketName, boolean isTrust) {
        GuiSettings menuSettings = isTrust
            ? droneSettings.guiConfig().trustPlayerSelection()
            : droneSettings.guiConfig().untrustPlayerSelection();

        int size;
        Inventory menu;
        if (isTrust) {
            // Trust mode: show all online players
            List<org.bukkit.entity.Player> onlinePlayers = new ArrayList<>();
            for (org.bukkit.entity.Player target : player.getServer().getOnlinePlayers()) {
                if (!target.equals(player)) {
                    onlinePlayers.add(target);
                }
            }

            size = Math.max(menuSettings.size(), Math.min(54, ((onlinePlayers.size() + 8) / 9) * 9));
            DroneMenuHandler.DroneMenuHolder holder = new DroneMenuHandler.DroneMenuHolder("trust_selection:" + socketName + ":" + isTrust);
            menu = player.getServer().createInventory(holder, size,
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

            // Add player heads
            for (int i = 0; i < onlinePlayers.size() && i < size; i++) {
                org.bukkit.entity.Player target = onlinePlayers.get(i);
                ItemStack headItem = createTrustPlayerHead(target);
                menu.setItem(i, headItem);
            }
        } else {
            // Untrust mode: show only trusted players (with their UUIDs stored)
            DeliverySocket socket = socketRepository.getSocket(player.getUniqueId(), socketName);
            List<UUID> trustedPlayerUUIDs = socket != null ? socket.trustedPlayers() : new ArrayList<>();

            size = Math.max(menuSettings.size(), Math.min(54, ((trustedPlayerUUIDs.size() + 8) / 9) * 9));
            DroneMenuHandler.DroneMenuHolder holder = new DroneMenuHandler.DroneMenuHolder("trust_selection:" + socketName + ":" + isTrust);
            menu = player.getServer().createInventory(holder, size,
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

            // Add trusted player heads (online only, but with UUID for offline handling)
            int slot = 0;
            for (UUID trustedUuid : trustedPlayerUUIDs) {
                if (slot >= size) break;
                org.bukkit.entity.Player trustedPlayer = org.bukkit.Bukkit.getPlayer(trustedUuid);
                if (trustedPlayer != null) {
                    ItemStack headItem = createUntrustPlayerHeadWithUUID(trustedPlayer, trustedUuid);
                    menu.setItem(slot, headItem);
                    slot++;
                }
            }
        }

        // Add back button if configured (this will override fill items and player heads if conflict)
        GuiItem backItem = menuSettings.items().get("back");
        if (backItem != null && backItem.position() >= 0 && backItem.position() < size) {
            ItemStack backButton = createMenuItem(backItem.material(), backItem.name(), backItem.lore());
            menu.setItem(backItem.position(), backButton);
        }

        player.openInventory(menu);
    }
}
