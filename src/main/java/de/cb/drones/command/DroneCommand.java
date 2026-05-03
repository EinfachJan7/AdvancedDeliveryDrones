package de.cb.drones.command;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.gui.DroneMenuHandler;
import de.cb.drones.socket.DeliverySocket;
import de.cb.drones.socket.SocketRepository;
// import de.cb.drones.socket.SocketBlacklistRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class DroneCommand implements CommandExecutor, TabCompleter, Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final AdvancedDeliveryDronesPlugin plugin;
    private final DroneManager droneManager;
    private final PlayerSettingsRepository settingsRepository;
    private final DroneMenuHandler menuHandler;
    private final SocketRepository socketRepository;
    // private final SocketBlacklistRepository blacklistRepository;

    public DroneCommand(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, PlayerSettingsRepository settingsRepository, DroneSettings droneSettings, SocketRepository socketRepository /*, SocketBlacklistRepository blacklistRepository */) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.settingsRepository = settingsRepository;
        this.socketRepository = socketRepository;
        // this.blacklistRepository = blacklistRepository;
        this.menuHandler = new DroneMenuHandler(plugin, droneManager, settingsRepository, droneSettings, socketRepository /*, blacklistRepository */);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    public void updateMenuHandlerSettings(DroneSettings newSettings) {
        if (menuHandler != null) {
            menuHandler.updateSettings(newSettings);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.component("only-players"));
            return true;
        }
        if (args.length == 0) {
            menuHandler.getMenuGUI().openMainMenu(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "send" -> executeSend(player, args);
            case "admin" -> executeAdmin(player, args);
            case "preview" -> executePreview(player, args);
            case "toggle" -> executeToggle(player);
            case "reload" -> executeReload(player);
            case "list" -> executeList(player);
            case "decline" -> executeDecline(player);
            case "socket" -> executeSocket(player, args);
            default -> {
                player.sendMessage("/drone <send|admin|preview|toggle|reload|list|decline|socket>");
                yield true;
            }
        };
    }

    private boolean executeSend(Player sender, String[] args) {
        if (!sender.hasPermission("drone.send")) {
            droneManager.sendMessage(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.component("usage-send"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            droneManager.sendMessage(sender, "player-offline");
            return true;
        }
        if (droneManager.isBlockedWorld(sender.getWorld().getName())) {
            droneManager.sendMessage(sender, "world-blocked", "<world>", sender.getWorld().getName());
            return true;
        }
        if (!settingsRepository.canReceive(target.getUniqueId())) {
            droneManager.sendMessage(sender, "toggled-off");
            return true;
        }
        if (!droneManager.canSenderLaunch(sender.getUniqueId())) {
            droneManager.sendMessage(sender, "sender-limit-reached", "<max>", String.valueOf(droneManager.maxActivePerSender()));
            return true;
        }

        prepareSendFlow(sender, target, null);
        return true;
    }

    private boolean executeAdmin(Player player, String[] args) {
        if (!player.hasPermission("drone.admin")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 2 || !"send".equalsIgnoreCase(args[1]) || args.length < 5) {
            player.sendMessage(plugin.component("usage-admin"));
            return true;
        }
        Location targetLocation = parseAdminTarget(player, args);
        if (targetLocation == null) {
            player.sendMessage(plugin.component("usage-admin"));
            return true;
        }
        if (droneManager.isBlockedWorld(player.getWorld().getName())) {
            droneManager.sendMessage(player, "world-blocked", "<world>", player.getWorld().getName());
            return true;
        }
        if (droneManager.isBlockedWorld(targetLocation.getWorld().getName())) {
            droneManager.sendMessage(player, "world-blocked", "<world>", targetLocation.getWorld().getName());
            return true;
        }
        if (!droneManager.canSenderLaunch(player.getUniqueId())) {
            droneManager.sendMessage(player, "sender-limit-reached", "<max>", String.valueOf(droneManager.maxActivePerSender()));
            return true;
        }
        prepareSendFlow(player, player, targetLocation);
        return true;
    }

    private boolean executeToggle(Player player) {
        if (!player.hasPermission("drone.use")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        boolean current = settingsRepository.canReceive(player.getUniqueId());
        settingsRepository.setCanReceive(player.getUniqueId(), !current);
        droneManager.sendMessage(player, current ? "toggle-off" : "toggle-on");
        return true;
    }

    private boolean executeReload(Player player) {
        if (!player.hasPermission("drone.admin")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        plugin.reloadPlugin();
        menuHandler.updateSettings(droneManager.settings());
        droneManager.sendMessage(player, "reload");
        return true;
    }

    
    private boolean executeList(Player player) {
        if (!player.hasPermission("drone.admin")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        List<de.cb.drones.drone.DeliveryDrone> drones = droneManager.activeDronesSnapshot();
        if (drones.isEmpty()) {
            player.sendMessage(plugin.component("no-active-drones"));
            return true;
        }
        droneManager.sendMessage(player, "active-drones-count", "<count>", String.valueOf(drones.size()));
        for (de.cb.drones.drone.DeliveryDrone drone : drones) {
            Location loc = drone.currentLocation();
            String command = "/tp " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
            Component line = MINI_MESSAGE.deserialize(
                    "<gray>- <yellow><receiver></yellow> <dark_gray>|</dark_gray> "
                            + "<white><world></white> <gray>(<x> <y> <z>)</gray> "
                            + "<click:run_command:'" + command + "'><green>[Teleport]</green></click>",
                    Placeholder.unparsed("receiver", drone.receiverName()),
                    Placeholder.unparsed("world", loc.getWorld() == null ? "world" : loc.getWorld().getName()),
                    Placeholder.unparsed("x", String.valueOf(loc.getBlockX())),
                    Placeholder.unparsed("y", String.valueOf(loc.getBlockY())),
                    Placeholder.unparsed("z", String.valueOf(loc.getBlockZ()))
            );
            player.sendMessage(line);
        }
        return true;
    }

    private boolean executeDecline(Player player) {
        if (!player.hasPermission("drone.use")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        int declined = droneManager.declineIncoming(player);
        if (declined <= 0) {
            droneManager.sendMessage(player, "decline-none");
            return true;
        }
        droneManager.sendMessage(player, "decline-success", "<count>", String.valueOf(declined));
        return true;
    }

    private boolean executeSocket(Player player, String[] args) {
        if (!player.hasPermission("drone.socket")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.component("usage-socket"));
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "place" -> executeSocketPlace(player, args);
            case "remove" -> executeSocketRemove(player, args);
            case "list" -> executeSocketList(player);
            case "send" -> executeSocketSend(player, args);
            case "manage" -> executeSocketManage(player);
            case "rename" -> executeSocketRename(player, args);
            case "trust" -> executeSocketTrust(player, args);
            case "untrust" -> executeSocketUntrust(player, args);
            default -> {
                player.sendMessage(plugin.component("usage-socket"));
                yield true;
            }
        };
    }

    private boolean executeSocketPlace(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.component("usage-socket-place"));
            return true;
        }
        String socketName = args[2];
        if (socketName.length() > 32) {
            droneManager.sendMessage(player, "socket-name-too-long");
            return true;
        }

        if (socketRepository.getSocketsByOwner(player.getUniqueId()).stream()
                .anyMatch(s -> s.name().equals(socketName))) {
            droneManager.sendMessage(player, "socket-exists", "<name>", socketName);
            return true;
        }

        if (droneManager.isBlockedWorld(player.getWorld().getName())) {
            droneManager.sendMessage(player, "world-blocked", "<world>", player.getWorld().getName());
            return true;
        }

        Location loc = player.getLocation();
        try {
            socketRepository.addSocket(player.getUniqueId(), player.getName(), socketName, loc);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Maximum") && e.getMessage().contains("socket(s) per player")) {
                droneManager.sendMessage(player, "socket-limit-reached", "<max>", String.valueOf(droneManager.settings().maxSocketsPerPlayer()));
                return true;
            }
            throw e;
        }

        droneManager.sendMessage(player, "socket-placed", "<name>", socketName);
        droneManager.sendMessage(player, "socket-location", "<coords>", loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        return true;
    }

    private boolean executeSocketRemove(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.component("usage-socket-remove"));
            return true;
        }
        String name = args[2];
        if (!socketRepository.socketNameExists(player.getUniqueId(), name)) {
            droneManager.sendMessage(player, "socket-not-found", "<name>", name);
            return true;
        }
        boolean removed = socketRepository.removeSocket(player.getUniqueId(), name);
        if (removed) {
            droneManager.sendMessage(player, "socket-removed", "<name>", name);
        } else {
            droneManager.sendMessage(player, "socket-remove-failed", "<name>", name);
        }
        return true;
    }

    private boolean executeSocketList(Player player) {
        List<DeliverySocket> sockets = socketRepository.getSocketsByOwner(player.getUniqueId());
        if (sockets.isEmpty()) {
            droneManager.sendMessage(player, "socket-none");
            return true;
        }
        droneManager.sendMessage(player, "socket-list-header", "<count>", String.valueOf(sockets.size()));
        for (DeliverySocket socket : sockets) {
            Component line = MINI_MESSAGE.deserialize(
                    "<gray>- <yellow><name></yellow> <dark_gray>|</dark_gray> "
                            + "<white><world></white> <gray>(<coords>)</gray> "
                            + "<click:run_command:'/drone socket send " + socket.name() + "'><green>[Send]</green></click> "
                            + "<click:run_command:'/drone socket remove " + socket.name() + "'><red>[Remove]</red></click>",
                    Placeholder.unparsed("name", socket.name()),
                    Placeholder.unparsed("world", socket.getWorldName()),
                    Placeholder.unparsed("coords", socket.getCoordinates())
            );
            player.sendMessage(line);
        }
        return true;
    }

    private boolean executeSocketManage(Player player) {
        menuHandler.getMenuGUI().openSocketManagementMenu(player);
        return true;
    }

    private boolean executeSocketRename(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("/drone socket rename <alter-name> <neuer-name>");
            return true;
        }
        String oldName = args[2];
        String newName = args[3];
        
        if (newName.length() > 32) {
            droneManager.sendMessage(player, "socket-name-too-long");
            return true;
        }

        DeliverySocket socket = socketRepository.getSocket(player.getUniqueId(), oldName);
        if (socket == null) {
            droneManager.sendMessage(player, "socket-not-found", "<name>", oldName);
            return true;
        }

        if (socketRepository.socketNameExists(player.getUniqueId(), newName)) {
            droneManager.sendMessage(player, "socket-exists", "<name>", newName);
            return true;
        }

        Location loc = socket.location();
        socketRepository.removeSocket(player.getUniqueId(), oldName);
        socketRepository.addSocket(player.getUniqueId(), player.getName(), newName, loc);
        droneManager.sendMessage(player, "socket-renamed", "<old>", oldName, "<new>", newName);
        return true;
    }

    private boolean executeSocketTrust(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("/drone socket trust <socket-name> <player>");
            return true;
        }
        String socketName = args[2];
        String targetPlayerName = args[3];

        DeliverySocket socket = socketRepository.getSocket(player.getUniqueId(), socketName);
        if (socket == null) {
            droneManager.sendMessage(player, "socket-not-found", "<name>", socketName);
            return true;
        }

        Player targetPlayer = Bukkit.getPlayerExact(targetPlayerName);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            droneManager.sendMessage(player, "player-offline");
            return true;
        }

        if (socketRepository.addTrustedPlayer(player.getUniqueId(), socketName, targetPlayer.getUniqueId())) {
            droneManager.sendMessage(player, "socket-trust-added", "<socket>", socketName, "<player>", targetPlayerName);
        } else {
            droneManager.sendMessage(player, "socket-trust-already", "<socket>", socketName, "<player>", targetPlayerName);
        }
        return true;
    }

    private boolean executeSocketUntrust(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("/drone socket untrust <socket-name> <player>");
            return true;
        }
        String socketName = args[2];
        String targetPlayerName = args[3];

        DeliverySocket socket = socketRepository.getSocket(player.getUniqueId(), socketName);
        if (socket == null) {
            droneManager.sendMessage(player, "socket-not-found", "<name>", socketName);
            return true;
        }

        Player targetPlayer = Bukkit.getPlayerExact(targetPlayerName);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            droneManager.sendMessage(player, "player-offline");
            return true;
        }

        if (socketRepository.removeTrustedPlayer(player.getUniqueId(), socketName, targetPlayer.getUniqueId())) {
            droneManager.sendMessage(player, "socket-trust-removed", "<socket>", socketName, "<player>", targetPlayerName);
        } else {
            droneManager.sendMessage(player, "socket-trust-not-found", "<socket>", socketName, "<player>", targetPlayerName);
        }
        return true;
    }

    private boolean executeSocketSend(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("/drone socket send <socket-name>");
            return true;
        }
        String socketName = args[2];

        // Search for socket across all players using stream for performance
        DeliverySocket targetSocket = socketRepository.getAllSockets().stream()
                .filter(socket -> socket.name().equals(socketName))
                .findFirst()
                .orElse(null);

        if (targetSocket == null) {
            droneManager.sendMessage(player, "socket-not-found", "<name>", socketName);
            return true;
        }

        // Get socket owner
        Player socketOwner = Bukkit.getPlayer(targetSocket.ownerId());
        if (socketOwner == null || !socketOwner.isOnline()) {
            droneManager.sendMessage(player, "player-offline");
            return true;
        }

        if (droneManager.isBlockedWorld(player.getWorld().getName())) {
            droneManager.sendMessage(player, "world-blocked", "<world>", player.getWorld().getName());
            return true;
        }
        if (droneManager.isBlockedWorld(targetSocket.getWorldName())) {
            droneManager.sendMessage(player, "world-blocked", "<world>", targetSocket.getWorldName());
            return true;
        }
                
        if (!droneManager.canSenderLaunch(player.getUniqueId())) {
            droneManager.sendMessage(player, "sender-limit-reached", "<max>", String.valueOf(droneManager.maxActivePerSender()));
            return true;
        }

        prepareSendFlow(player, socketOwner, targetSocket.location(), true, targetSocket.name());
        return true;
    }

    @EventHandler
    public void onComposeInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player sender)) {
            return;
        }
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof ComposeInventoryHolder holder)) {
            return;
        }
        if (!holder.senderId().equals(sender.getUniqueId())) {
            return;
        }
        if (holder.animalsOnly()) {
            inv.clear();
            Inventory deliveryInventory = Bukkit.createInventory(
                    new DroneInventoryHolder(holder.senderId(), holder.receiverId()),
                    droneManager.settings().inventorySize(),
                    MINI_MESSAGE.deserialize(plugin.getConfig().getString("messages.drone-inventory-title", "<gold>Delivery Drone</gold>"))
            );
            spawnDroneFromSelection(sender, holder, deliveryInventory);
            return;
        }
        if (isInventoryEmpty(inv)) {
            return;
        }
        Player receiver = Bukkit.getPlayer(holder.receiverId());
        if (receiver == null || !receiver.isOnline()) {
            droneManager.sendMessage(sender, "player-offline");
            return;
        }
        Inventory deliveryInventory = Bukkit.createInventory(
                new DroneInventoryHolder(holder.senderId(), holder.receiverId()),
                inv.getSize(),
                MINI_MESSAGE.deserialize(plugin.getConfig().getString("messages.drone-inventory-title", "<gold>Delivery Drone</gold>"))
        );
        deliveryInventory.setContents(inv.getContents());
        inv.clear();

        spawnDroneFromSelection(sender, holder, deliveryInventory);
    }

    private List<LivingEntity> resolveSelectedAnimals(UUID senderId, List<UUID> selectedAnimalIds) {
        if (selectedAnimalIds == null || selectedAnimalIds.isEmpty()) {
            return List.of();
        }
        List<LivingEntity> selected = new ArrayList<>();
        for (UUID animalId : selectedAnimalIds) {
            Entity entity = Bukkit.getEntity(animalId);
            if (!(entity instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            if (!living.isLeashed()) {
                continue;
            }
            Entity leashHolder = living.getLeashHolder();
            if (leashHolder != null && leashHolder.getUniqueId().equals(senderId)) {
                selected.add(living);
            }
        }
        return selected;
    }

    private List<LivingEntity> listSenderLeashedAnimals(Player sender) {
        List<LivingEntity> attached = new ArrayList<>();
        for (Entity entity : sender.getNearbyEntities(12.0, 12.0, 12.0)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (!living.isLeashed()) {
                continue;
            }
            Entity leashHolder = living.getLeashHolder();
            if (leashHolder != null && leashHolder.getUniqueId().equals(sender.getUniqueId())) {
                attached.add(living);
            }
        }
        return attached;
    }

    private boolean executePreview(Player player, String[] args) {
        if (!player.hasPermission("drone.use")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("/drone preview <drone-id>");
            return true;
        }
        UUID droneId;
        try {
            droneId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException ex) {
            droneManager.sendMessage(player, "preview-unavailable");
            return true;
        }
        de.cb.drones.drone.DeliveryDrone drone = droneManager.findByDroneId(droneId);
        if (drone == null || !drone.receiverId().equals(player.getUniqueId())) {
            droneManager.sendMessage(player, "preview-unavailable");
            return true;
        }
        int previewSize = drone.animalsOnlyDelivery() ? 9 : drone.inventory().getSize();
        Inventory preview = Bukkit.createInventory(
                new PreviewInventoryHolder(drone.droneId(), player.getUniqueId()),
                previewSize,
                MINI_MESSAGE.deserialize(plugin.getConfig().getString("messages.drone-preview-title", "<gold>Drone Vorschau</gold>"))
        );
        if (drone.animalsOnlyDelivery()) {
            populateAnimalPreview(preview, drone.attachedAnimalTypes());
        } else {
            preview.setContents(drone.inventory().getContents());
        }
        player.openInventory(preview);
        return true;
    }

    private void populateAnimalPreview(Inventory preview, List<EntityType> animalTypes) {
        if (animalTypes == null || animalTypes.isEmpty()) {
            return;
        }
        Map<EntityType, Integer> counts = new HashMap<>();
        for (EntityType type : animalTypes) {
            counts.merge(type, 1, Integer::sum);
        }
        int slot = 0;
        for (Map.Entry<EntityType, Integer> entry : counts.entrySet()) {
            if (slot >= preview.getSize()) {
                break;
            }
            Material egg = resolveSpawnEgg(entry.getKey());
            ItemStack stack = new ItemStack(egg, Math.min(64, entry.getValue()));
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                String animalName = plugin.getConfig().getString("messages.animal-display-name", "<gold>Tier: <type></gold>")
                    .replace("<type>", entry.getKey().name());
                String animalCount = plugin.getConfig().getString("messages.animal-display-count", "<gray>Anzahl: <count></gray>")
                    .replace("<count>", String.valueOf(entry.getValue()));
                meta.displayName(MINI_MESSAGE.deserialize(animalName));
                meta.lore(List.of(MINI_MESSAGE.deserialize(animalCount)));
                stack.setItemMeta(meta);
            }
            preview.setItem(slot, stack);
            slot++;
        }
    }

    private Material resolveSpawnEgg(EntityType type) {
        Material exact = Material.getMaterial(type.name() + "_SPAWN_EGG");
        if (exact != null) {
            return exact;
        }
        if ("MUSHROOM_COW".equals(type.name()) || "MOOSHROOM".equals(type.name())) {
            Material mooshroom = Material.getMaterial("MOOSHROOM_SPAWN_EGG");
            if (mooshroom != null) {
                return mooshroom;
            }
        }
        Material matched = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return matched != null ? matched : Material.EGG;
    }

    private boolean isInventoryEmpty(Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("send", "admin", "preview", "toggle", "reload", "list", "decline", "socket");
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            return List.of("send");
        }
        if (args.length == 6 && "admin".equalsIgnoreCase(args[0]) && "send".equalsIgnoreCase(args[1])) {
            List<String> worlds = new ArrayList<>();
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                worlds.add(world.getName());
            }
            return worlds;
        }
        if (args.length == 2 && "send".equalsIgnoreCase(args[0])) {
            List<String> results = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (settingsRepository.canReceive(online.getUniqueId())) {
                    results.add(online.getName());
                }
            }
            return results;
        }
        if (args.length == 2 && "socket".equalsIgnoreCase(args[0])) {
            return List.of("place", "remove", "list", "send", "manage", "rename", "trust", "untrust");
        }
        if (args.length == 3 && "socket".equalsIgnoreCase(args[0])) {
            if (sender instanceof Player player) {
                if ("remove".equalsIgnoreCase(args[1])) {
                    // Only own sockets for remove - use stream for efficiency
                    return socketRepository.getSocketsByOwner(player.getUniqueId()).stream()
                            .map(DeliverySocket::name)
                            .toList();
                }
                if ("send".equalsIgnoreCase(args[1])) {
                    // All sockets for send - use stream for efficiency
                    return socketRepository.getAllSockets().stream()
                            .map(DeliverySocket::name)
                            .toList();
                }
                if ("trust".equalsIgnoreCase(args[1]) || "untrust".equalsIgnoreCase(args[1])) {
                    // Only own sockets for trust/untrust
                    return socketRepository.getSocketsByOwner(player.getUniqueId()).stream()
                            .map(DeliverySocket::name)
                            .toList();
                }
            }
        }
        if (args.length == 4 && "socket".equalsIgnoreCase(args[0])) {
            if ("trust".equalsIgnoreCase(args[1]) || "untrust".equalsIgnoreCase(args[1])) {
                // Return online player names
                List<String> results = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    results.add(online.getName());
                }
                return results;
            }
        }
        return Collections.emptyList();
    }

    @EventHandler
    public void onPreviewClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewInventoryHolder) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof SendModeInventoryHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player sender)) {
            return;
        }
        if (!holder.senderId().equals(sender.getUniqueId())) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        
        // Cache config items to avoid multiple lookups
        var sendModeItems = droneManager.settings().guiConfig().sendMode().items();
        Material animalsMaterial = sendModeItems.get("animals").material();
        Material itemsMaterial = sendModeItems.get("items").material();
        Material clickedType = clicked.getType();
        
        if (clickedType == animalsMaterial) {
            Bukkit.getScheduler().runTask(plugin, () -> sendAnimalsOnly(sender, holder));
            return;
        }
        if (clickedType == itemsMaterial) {
            openComposeInventory(sender, holder.receiverId(), holder.fixedTarget(), false);
        }
    }

    @EventHandler
    public void onPreviewDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewInventoryHolder
                || event.getView().getTopInventory().getHolder() instanceof SendModeInventoryHolder) {
            event.setCancelled(true);
        }
    }

    private Location parseAdminTarget(Player player, String[] args) {
        try {
            double x = Double.parseDouble(args[2]);
            double y = Double.parseDouble(args[3]);
            double z = Double.parseDouble(args[4]);
            org.bukkit.World world = args.length >= 6 ? Bukkit.getWorld(args[5]) : player.getWorld();
            if (world == null) {
                return null;
            }
            return new Location(world, x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatTarget(Location target) {
        String worldName = target.getWorld() == null ? "world" : target.getWorld().getName();
        return worldName + " " + target.getBlockX() + " " + target.getBlockY() + " " + target.getBlockZ();
    }

    private void prepareSendFlow(Player sender, Player receiver, Location fixedTarget) {
        prepareSendFlow(sender, receiver, fixedTarget, false, null);
    }

    private void prepareSendFlow(Player sender, Player receiver, Location fixedTarget, boolean exactSocketTarget) {
        prepareSendFlow(sender, receiver, fixedTarget, exactSocketTarget, null);
    }

    private void prepareSendFlow(Player sender, Player receiver, Location fixedTarget, boolean exactSocketTarget, String socketName) {
        if (droneManager.settings().carryLeashedAnimals()) {
            List<LivingEntity> leashedAnimals = listSenderLeashedAnimals(sender);
            if (!leashedAnimals.isEmpty()) {
                int maxAnimals = droneManager.settings().maxLeashedAnimalsPerDrone();
                if (maxAnimals > 0 && leashedAnimals.size() > maxAnimals) {
                    droneManager.sendMessage(sender, "too-many-leashed-animals", "<max>", String.valueOf(maxAnimals));
                    return;
                }
                Inventory selector = Bukkit.createInventory(
                        new SendModeInventoryHolder(
                                sender.getUniqueId(),
                                receiver.getUniqueId(),
                                fixedTarget,
                                leashedAnimals.stream().map(LivingEntity::getUniqueId).toList(),
                                fixedTarget != null && sender.getUniqueId().equals(receiver.getUniqueId()),
                                exactSocketTarget,
                                socketName
                        ),
                        9,
                        MINI_MESSAGE.deserialize(droneManager.settings().guiConfig().sendMode().title())
                );
                selector.setItem(3, createModeItem(
                        droneManager.settings().guiConfig().sendMode().items().get("animals").material(),
                        droneManager.settings().guiConfig().sendMode().items().get("animals").name(),
                        droneManager.settings().guiConfig().sendMode().items().get("animals").lore()
                ));
                selector.setItem(5, createModeItem(
                        droneManager.settings().guiConfig().sendMode().items().get("items").material(),
                        droneManager.settings().guiConfig().sendMode().items().get("items").name(),
                        droneManager.settings().guiConfig().sendMode().items().get("items").lore()
                ));
                sender.openInventory(selector);
                return;
            }
        }
        openComposeInventory(sender, receiver.getUniqueId(), fixedTarget, false, exactSocketTarget, socketName);
    }

    private void openComposeInventory(Player sender, UUID receiverId, Location fixedTarget, boolean animalsOnly) {
        openComposeInventory(sender, receiverId, fixedTarget, animalsOnly, false, null);
    }

    private void openComposeInventory(Player sender, UUID receiverId, Location fixedTarget, boolean animalsOnly, boolean exactSocketTarget) {
        openComposeInventory(sender, receiverId, fixedTarget, animalsOnly, exactSocketTarget, null);
    }

    private void openComposeInventory(Player sender, UUID receiverId, Location fixedTarget, boolean animalsOnly, boolean exactSocketTarget, String socketName) {
        Player receiver = Bukkit.getPlayer(receiverId);
        if (receiver == null || !receiver.isOnline()) {
            droneManager.sendMessage(sender, "player-offline");
            return;
        }
        int size = droneManager.settings().inventorySize();
        boolean adminSend = fixedTarget != null && sender.getUniqueId().equals(receiverId);
        ComposeInventoryHolder holder = new ComposeInventoryHolder(sender.getUniqueId(), receiverId, fixedTarget, animalsOnly, adminSend, List.of(), exactSocketTarget, socketName);
        String titleKey = socketName != null ? "drone-title-socket" : (fixedTarget == null ? "drone-title-player" : "drone-title-admin");
        String placeholder = socketName != null ? "<socket>" : "<player>";
        String value = socketName != null ? socketName : receiver.getName();
        Component titleComponent = getComponentMessageWithoutPrefix(titleKey, placeholder, value);
        Inventory compose = Bukkit.createInventory(holder, size, titleComponent);
        sender.openInventory(compose);
        if (socketName != null) {
            droneManager.sendMessage(sender, "open-inventory-socket", "<socket>", socketName, "<player>", receiver.getName());
        } else {
            droneManager.sendMessage(sender, "open-inventory", "<player>", receiver.getName());
        }
    }

    private ItemStack createModeItem(Material material, String title, List<String> loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(MINI_MESSAGE.deserialize(title));
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(MINI_MESSAGE.deserialize(line));
                }
                meta.lore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Component getComponentMessageWithoutPrefix(String key, String placeholder, String value) {
        String body = plugin.getConfig().getString("messages." + key, key);
        if (placeholder != null && value != null) {
            body = body.replace(placeholder, value);
        }
        return MINI_MESSAGE.deserialize(body);
    }

    private void sendAnimalsOnly(Player sender, SendModeInventoryHolder holder) {
        Inventory deliveryInventory = Bukkit.createInventory(
                new DroneInventoryHolder(holder.senderId(), holder.receiverId()),
                droneManager.settings().inventorySize(),
                MINI_MESSAGE.deserialize(plugin.getConfig().getString("messages.drone-inventory-title", "<gold>Delivery Drone</gold>"))
        );
        ComposeInventoryHolder composeHolder = new ComposeInventoryHolder(
                holder.senderId(),
                holder.receiverId(),
                holder.fixedTarget(),
                true,
                holder.adminSend(),
                holder.selectedAnimalIds(),
                holder.exactSocketTarget(),
                holder.socketName()
        );
        spawnDroneFromSelection(sender, composeHolder, deliveryInventory);
        sender.closeInventory();
    }

    private void spawnDroneFromSelection(Player sender, ComposeInventoryHolder holder, Inventory deliveryInventory) {
        Player receiver = Bukkit.getPlayer(holder.receiverId());
        if (receiver == null || !receiver.isOnline()) {
            droneManager.sendMessage(sender, "player-offline");
            return;
        }
        List<LivingEntity> attachedAnimals = holder.animalsOnly()
                ? resolveSelectedAnimals(holder.senderId(), holder.selectedAnimalIds())
                : List.of();
        Location targetLocation = holder.fixedTarget() != null ? holder.fixedTarget() : receiver.getLocation().clone();
        de.cb.drones.drone.DeliveryDrone drone = droneManager.spawnDrone(
                sender,
                receiver,
                deliveryInventory,
                targetLocation,
                attachedAnimals,
                holder.animalsOnly(),
                holder.adminSend(),
                holder.exactSocketTarget(),
                holder.socketName()
        );
        if (drone == null) {
            return;
        }
        droneManager.sendMessage(sender, "sent-success", "<player>", holder.socketName() != null ? holder.socketName() : receiver.getName());
        Component incoming = MINI_MESSAGE.deserialize(
                droneManager.message("incoming-drone", "<player>", sender.getName())
                        + " <click:run_command:'/drone preview " + drone.droneId() + "'>"
                        + "<green><bold>[Vorschau]</bold></green></click>"
        );
        receiver.sendMessage(incoming);
    }

    private record ComposeInventoryHolder(
            UUID senderId,
            UUID receiverId,
            Location fixedTarget,
            boolean animalsOnly,
            boolean adminSend,
            List<UUID> selectedAnimalIds,
            boolean exactSocketTarget,
            String socketName
    ) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record SendModeInventoryHolder(
            UUID senderId,
            UUID receiverId,
            Location fixedTarget,
            List<UUID> selectedAnimalIds,
            boolean adminSend,
            boolean exactSocketTarget,
            String socketName
    ) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record DroneInventoryHolder(UUID senderId, UUID receiverId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record PreviewInventoryHolder(UUID droneId, UUID receiverId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
