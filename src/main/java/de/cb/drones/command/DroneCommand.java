package de.cb.drones.command;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.gui.DroneMenuHandler;
import de.cb.drones.socket.DeliverySocket;
import de.cb.drones.socket.SocketRepository;
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

    public DroneCommand(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, PlayerSettingsRepository settingsRepository, DroneSettings droneSettings, SocketRepository socketRepository) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.settingsRepository = settingsRepository;
        this.menuHandler = new DroneMenuHandler(plugin, droneManager, settingsRepository, droneSettings, socketRepository);
        this.socketRepository = socketRepository;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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
            sender.sendMessage(droneManager.message("no-permission", null, null));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.component("usage-send"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(droneManager.message("player-offline", null, null));
            return true;
        }
        if (droneManager.isBlockedWorld(sender.getWorld().getName())) {
            sender.sendMessage(droneManager.message("world-blocked", "<world>", sender.getWorld().getName()));
            return true;
        }
        if (!settingsRepository.canReceive(target.getUniqueId())) {
            sender.sendMessage(droneManager.message("toggled-off", null, null));
            return true;
        }
        if (!droneManager.canSenderLaunch(sender.getUniqueId())) {
            sender.sendMessage(droneManager.message("sender-limit-reached", "<max>", String.valueOf(droneManager.maxActivePerSender())));
            return true;
        }

        prepareSendFlow(sender, target, null);
        return true;
    }

    private boolean executeAdmin(Player player, String[] args) {
        if (!player.hasPermission("drone.admin")) {
            player.sendMessage(droneManager.message("no-permission", null, null));
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
            player.sendMessage(droneManager.message("world-blocked", "<world>", player.getWorld().getName()));
            return true;
        }
        if (droneManager.isBlockedWorld(targetLocation.getWorld().getName())) {
            player.sendMessage(droneManager.message("world-blocked", "<world>", targetLocation.getWorld().getName()));
            return true;
        }
        if (!droneManager.canSenderLaunch(player.getUniqueId())) {
            player.sendMessage(droneManager.message("sender-limit-reached", "<max>", String.valueOf(droneManager.maxActivePerSender())));
            return true;
        }
        prepareSendFlow(player, player, targetLocation);
        return true;
    }

    private boolean executeToggle(Player player) {
        if (!player.hasPermission("drone.use")) {
            player.sendMessage(droneManager.message("no-permission", null, null));
            return true;
        }
        boolean current = settingsRepository.canReceive(player.getUniqueId());
        settingsRepository.setCanReceive(player.getUniqueId(), !current);
        player.sendMessage(droneManager.message(current ? "toggle-off" : "toggle-on", null, null));
        return true;
    }

    private boolean executeReload(Player player) {
        if (!player.hasPermission("drone.admin")) {
            player.sendMessage(droneManager.message("no-permission", null, null));
            return true;
        }
        plugin.reloadPlugin();
        menuHandler.updateSettings(droneManager.settings());
        player.sendMessage(droneManager.message("reload", null, null));
        return true;
    }

    
    private boolean executeList(Player player) {
        if (!player.hasPermission("drone.admin")) {
            player.sendMessage(droneManager.message("no-permission", null, null));
            return true;
        }
        List<de.cb.drones.drone.DeliveryDrone> drones = droneManager.activeDronesSnapshot();
        if (drones.isEmpty()) {
            player.sendMessage(plugin.component("no-active-drones"));
            return true;
        }
        player.sendMessage(droneManager.message("active-drones-count", "<count>", String.valueOf(drones.size())));
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
            player.sendMessage(droneManager.message("no-permission", null, null));
            return true;
        }
        int declined = droneManager.declineIncoming(player);
        if (declined <= 0) {
            player.sendMessage(droneManager.message("decline-none", null, null));
            return true;
        }
        player.sendMessage(droneManager.message("decline-success", "<count>", String.valueOf(declined)));
        return true;
    }

    private boolean executeSocket(Player player, String[] args) {
        if (!player.hasPermission("drone.socket")) {
            player.sendMessage(droneManager.message("no-permission", null, null));
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
            player.sendMessage(droneManager.message("socket-name-too-long", null, null));
            return true;
        }

        if (socketRepository.getSocketsByOwner(player.getUniqueId()).stream()
                .anyMatch(s -> s.name().equals(socketName))) {
            player.sendMessage(droneManager.message("socket-exists", "<name>", socketName));
            return true;
        }

        if (droneManager.isBlockedWorld(player.getWorld().getName())) {
            player.sendMessage(droneManager.message("world-blocked", "<world>", player.getWorld().getName()));
            return true;
        }

        Location loc = player.getLocation();
        socketRepository.addSocket(player.getUniqueId(), socketName, player.getName(), loc);

        player.sendMessage(droneManager.message("socket-placed", "<name>", socketName));
        player.sendMessage(droneManager.message("socket-location", "<coords>", loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
        return true;
    }

    private boolean executeSocketRemove(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.component("usage-socket-remove"));
            return true;
        }
        String name = args[2];
        if (!socketRepository.socketNameExists(player.getUniqueId(), name)) {
            player.sendMessage(droneManager.message("socket-not-found", "<name>", name));
            return true;
        }
        boolean removed = socketRepository.removeSocket(player.getUniqueId(), name);
        if (removed) {
            player.sendMessage(droneManager.message("socket-removed", "<name>", name));
        } else {
            player.sendMessage(droneManager.message("socket-remove-failed", "<name>", name));
        }
        return true;
    }

    private boolean executeSocketList(Player player) {
        List<DeliverySocket> sockets = socketRepository.getSocketsByOwner(player.getUniqueId());
        if (sockets.isEmpty()) {
            player.sendMessage(droneManager.message("socket-none", null, null));
            return true;
        }
        player.sendMessage(droneManager.message("socket-list-header", "<count>", String.valueOf(sockets.size())));
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

    private boolean executeSocketSend(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("/drone socket send <socket-name>");
            return true;
        }
        String socketName = args[2];

        // Search for socket across all players
        DeliverySocket targetSocket = null;
        for (DeliverySocket socket : socketRepository.getAllSockets()) {
            if (socket.name().equals(socketName)) {
                targetSocket = socket;
                break;
            }
        }

        if (targetSocket == null) {
            player.sendMessage(droneManager.message("socket-not-found", "<name>", socketName));
            return true;
        }

        // Get socket owner
        Player socketOwner = Bukkit.getPlayer(targetSocket.ownerId());
        if (socketOwner == null || !socketOwner.isOnline()) {
            player.sendMessage(droneManager.message("player-offline", null, null));
            return true;
        }

        if (droneManager.isBlockedWorld(player.getWorld().getName())) {
            player.sendMessage(droneManager.message("world-blocked", "<world>", player.getWorld().getName()));
            return true;
        }
        if (droneManager.isBlockedWorld(targetSocket.getWorldName())) {
            player.sendMessage(droneManager.message("world-blocked", "<world>", targetSocket.getWorldName()));
            return true;
        }
        if (!droneManager.canSenderLaunch(player.getUniqueId())) {
            player.sendMessage(droneManager.message("sender-limit-reached", "<max>", String.valueOf(droneManager.maxActivePerSender())));
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
                    Component.text("Delivery Drone")
            );
            spawnDroneFromSelection(sender, holder, deliveryInventory);
            return;
        }
        if (isInventoryEmpty(inv)) {
            return;
        }
        Player receiver = Bukkit.getPlayer(holder.receiverId());
        if (receiver == null || !receiver.isOnline()) {
            sender.sendMessage(droneManager.message("player-offline", null, null));
            return;
        }
        Inventory deliveryInventory = Bukkit.createInventory(
                new DroneInventoryHolder(holder.senderId(), holder.receiverId()),
                inv.getSize(),
                Component.text("Delivery Drone")
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
            player.sendMessage(droneManager.message("no-permission", null, null));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("/drone preview <id>");
            return true;
        }
        UUID droneId;
        try {
            droneId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(droneManager.message("preview-unavailable", null, null));
            return true;
        }
        de.cb.drones.drone.DeliveryDrone drone = droneManager.findByDroneId(droneId);
        if (drone == null || !drone.receiverId().equals(player.getUniqueId())) {
            player.sendMessage(droneManager.message("preview-unavailable", null, null));
            return true;
        }
        int previewSize = drone.animalsOnlyDelivery() ? 9 : drone.inventory().getSize();
        Inventory preview = Bukkit.createInventory(
                new PreviewInventoryHolder(drone.droneId(), player.getUniqueId()),
                previewSize,
                Component.text("Drone Vorschau")
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
                meta.displayName(Component.text("Tier: " + entry.getKey().name()));
                meta.lore(List.of(Component.text("Anzahl: " + entry.getValue())));
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
            return List.of("place", "remove", "list", "send");
        }
        if (args.length == 3 && "socket".equalsIgnoreCase(args[0])) {
            if (sender instanceof Player player) {
                if ("remove".equalsIgnoreCase(args[1])) {
                    // Only own sockets for remove
                    List<String> socketNames = new ArrayList<>();
                    for (DeliverySocket socket : socketRepository.getSocketsByOwner(player.getUniqueId())) {
                        socketNames.add(socket.name());
                    }
                    return socketNames;
                }
                if ("send".equalsIgnoreCase(args[1])) {
                    // All sockets for send
                    List<String> socketNames = new ArrayList<>();
                    for (DeliverySocket socket : socketRepository.getAllSockets()) {
                        socketNames.add(socket.name());
                    }
                    return socketNames;
                }
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
        if (clicked.getType() == droneManager.settings().guiConfig().sendMode().items().get("animals").material()) {
            Bukkit.getScheduler().runTask(plugin, () -> sendAnimalsOnly(sender, holder));
            return;
        }
        if (clicked.getType() == droneManager.settings().guiConfig().sendMode().items().get("items").material()) {
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
                    sender.sendMessage(droneManager.message("too-many-leashed-animals", "<max>", String.valueOf(maxAnimals)));
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
            sender.sendMessage(droneManager.message("player-offline", null, null));
            return;
        }
        int size = droneManager.settings().inventorySize();
        boolean adminSend = fixedTarget != null && sender.getUniqueId().equals(receiverId);
        ComposeInventoryHolder holder = new ComposeInventoryHolder(sender.getUniqueId(), receiverId, fixedTarget, animalsOnly, adminSend, List.of(), exactSocketTarget, socketName);
        String title = socketName != null ? "Drone > " + socketName : (fixedTarget == null ? "Drone > " + receiver.getName() : "Socket Drone > " + receiver.getName());
        Inventory compose = Bukkit.createInventory(holder, size, Component.text(title));
        sender.openInventory(compose);
        sender.sendMessage(droneManager.message("open-inventory", "<player>", receiver.getName()));
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

    private void sendAnimalsOnly(Player sender, SendModeInventoryHolder holder) {
        Inventory deliveryInventory = Bukkit.createInventory(
                new DroneInventoryHolder(holder.senderId(), holder.receiverId()),
                droneManager.settings().inventorySize(),
                Component.text("Delivery Drone")
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
            sender.sendMessage(droneManager.message("player-offline", null, null));
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
        sender.sendMessage(droneManager.message("sent-success", "<player>", holder.socketName() != null ? holder.socketName() : receiver.getName()));
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
