package de.cb.drones.command;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.drone.DroneManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

public final class DroneCommand implements CommandExecutor, TabCompleter, Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final AdvancedDeliveryDronesPlugin plugin;
    private final DroneManager droneManager;
    private final PlayerSettingsRepository settingsRepository;

    public DroneCommand(AdvancedDeliveryDronesPlugin plugin, DroneManager droneManager, PlayerSettingsRepository settingsRepository) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.settingsRepository = settingsRepository;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("/drone <send|admin|preview|toggle|reload|list|decline>");
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
            default -> {
                player.sendMessage("/drone <send|admin|preview|toggle|reload|list|decline>");
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
            sender.sendMessage("/drone send <Player>");
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
        if (droneManager.isBlockedWorld(target.getWorld().getName())) {
            sender.sendMessage(droneManager.message("world-blocked", "<world>", target.getWorld().getName()));
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
        int size = droneManager.settings().inventorySize();
        ComposeInventoryHolder holder = new ComposeInventoryHolder(sender.getUniqueId(), target.getUniqueId(), null);
        Inventory compose = Bukkit.createInventory(holder, size, Component.text("Drone > " + target.getName()));
        sender.openInventory(compose);
        sender.sendMessage(droneManager.message("open-inventory", "<player>", target.getName()));
        return true;
    }

    private boolean executeAdmin(Player player, String[] args) {
        if (!player.hasPermission("drone.admin")) {
            player.sendMessage(droneManager.message("no-permission", null, null));
            return true;
        }
        if (args.length < 2 || !"send".equalsIgnoreCase(args[1]) || args.length < 5) {
            player.sendMessage("/drone admin send <x> <y> <z> [world]");
            return true;
        }
        Location targetLocation = parseAdminTarget(player, args);
        if (targetLocation == null) {
            player.sendMessage("/drone admin send <x> <y> <z> [world]");
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
        int size = droneManager.settings().inventorySize();
        ComposeInventoryHolder holder = new ComposeInventoryHolder(player.getUniqueId(), player.getUniqueId(), targetLocation);
        Inventory compose = Bukkit.createInventory(holder, size, Component.text("Admin Drone > " + formatTarget(targetLocation)));
        player.openInventory(compose);
        player.sendMessage(droneManager.message("open-inventory", "<player>", "deiner Ziel-Koordinate"));
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
            player.sendMessage("Keine aktiven Drohnen.");
            return true;
        }
        player.sendMessage("Aktive Drohnen: " + drones.size());
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

        List<LivingEntity> attachedAnimals = findSenderLeashedAnimals(sender);
        Location targetLocation = holder.fixedTarget() != null ? holder.fixedTarget() : receiver.getLocation().clone();
        de.cb.drones.drone.DeliveryDrone drone = droneManager.spawnDrone(sender, receiver, deliveryInventory, targetLocation, attachedAnimals);
        if (drone != null) {
            sender.sendMessage(droneManager.message("sent-success", "<player>", receiver.getName()));
            Component incoming = MINI_MESSAGE.deserialize(
                    droneManager.message("incoming-drone", "<player>", sender.getName())
                            + " <click:run_command:'/drone preview " + drone.droneId() + "'>"
                            + "<green><bold>[Vorschau]</bold></green></click>"
            );
            receiver.sendMessage(incoming);
        }
    }

    private List<LivingEntity> findSenderLeashedAnimals(Player sender) {
        if (!droneManager.settings().carryLeashedAnimals()) {
            return List.of();
        }
        int limit = droneManager.settings().maxLeashedAnimalsPerDrone();
        if (limit <= 0) {
            return List.of();
        }
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
                if (attached.size() >= limit) {
                    break;
                }
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
        Inventory preview = Bukkit.createInventory(
                new PreviewInventoryHolder(drone.droneId(), player.getUniqueId()),
                drone.inventory().getSize(),
                Component.text("Drone Vorschau")
        );
        preview.setContents(drone.inventory().getContents());
        player.openInventory(preview);
        return true;
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
            return List.of("send", "admin", "preview", "toggle", "reload", "list", "decline");
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
        return Collections.emptyList();
    }

    @EventHandler
    public void onPreviewClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewInventoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPreviewDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewInventoryHolder) {
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

    private record ComposeInventoryHolder(UUID senderId, UUID receiverId, Location fixedTarget) implements InventoryHolder {
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
