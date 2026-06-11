package de.cb.drones.command;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.config.ComposeDraftRepository;
import de.cb.drones.config.ComposeDraftRepository.StoredComposeDraft;
import de.cb.drones.config.PlayerBlacklistRepository;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.drone.GuiItem;
import de.cb.drones.drone.GuiSettings;
import de.cb.drones.gui.DroneMenuHandler;
import de.cb.drones.gui.GuiItemStacks;
import de.cb.drones.socket.DeliverySocket;
import de.cb.drones.socket.SocketRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class DroneCommand implements CommandExecutor, TabCompleter, Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final AdvancedDeliveryDronesPlugin plugin;
    private final DroneManager droneManager;
    private final PlayerSettingsRepository settingsRepository;
    private final PlayerBlacklistRepository blacklistRepository;
    private final DroneMenuHandler menuHandler;
    private final SocketRepository socketRepository;
    private DroneSettings droneSettings;
    private final Map<UUID, PendingSendDraft> sendDrafts = new HashMap<>();
    private final Map<UUID, Boolean> composeHubAnimalsOnly = new HashMap<>();
    private final Set<UUID> suppressComposeHubReopen = new HashSet<>();
    private final ComposeDraftRepository composeDraftRepository;

    public DroneCommand(
            AdvancedDeliveryDronesPlugin plugin,
            DroneManager droneManager,
            PlayerSettingsRepository settingsRepository,
            PlayerBlacklistRepository blacklistRepository,
            DroneSettings droneSettings,
            SocketRepository socketRepository
    ) {
        this.plugin = plugin;
        this.droneManager = droneManager;
        this.settingsRepository = settingsRepository;
        this.blacklistRepository = blacklistRepository;
        this.socketRepository = socketRepository;
        this.droneSettings = droneSettings;
        this.composeDraftRepository = new ComposeDraftRepository(plugin);
        this.menuHandler = new DroneMenuHandler(plugin, droneManager, settingsRepository, blacklistRepository, droneSettings, socketRepository);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadPersistedComposeDrafts();
    }

    public void saveComposeDrafts() {
        composeDraftRepository.clearAll();
        for (Map.Entry<UUID, PendingSendDraft> entry : sendDrafts.entrySet()) {
            UUID senderId = entry.getKey();
            boolean animalsOnly = composeHubAnimalsOnly.getOrDefault(senderId, entry.getValue().animalsOnlyMode());
            composeDraftRepository.save(senderId, toStoredDraft(entry.getValue(), animalsOnly));
        }
    }

    public void reloadComposeDrafts() {
        composeDraftRepository.reload();
        sendDrafts.clear();
        composeHubAnimalsOnly.clear();
        loadPersistedComposeDrafts();
    }
    
    public void updateMenuHandlerSettings(DroneSettings newSettings) {
        this.droneSettings = newSettings;
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
            case "convert" -> executeConvert(player, args);
            case "list" -> executeList(player);
            case "decline" -> executeDecline(player);
            case "cancel" -> executeCancel(player);
            case "socket" -> executeSocket(player, args);
            case "blacklist" -> executeBlacklist(player, args);
            case "config" -> executeConfig(player);
            case "locate" -> {
                if (!droneSettings.locateParticlesEnabled()) {
                    player.sendMessage(plugin.component("usage-main"));
                    yield true;
                }
                yield executeLocate(player);
            }
            default -> {
                if (droneSettings.locateParticlesEnabled()) {
                    String prefix = plugin.getLanguageManager().getString("prefix", "");
                    String body = plugin.getLanguageManager().getString("usage-main", "");
                    if (body.contains("blacklist>")) {
                        body = body.replace("blacklist>", "blacklist|locate>");
                    }
                    player.sendMessage(MINI_MESSAGE.deserialize(prefix + body));
                } else {
                    player.sendMessage(plugin.component("usage-main"));
                }
                yield true;
            }
        };
    }

    private boolean executeSend(Player sender, String[] args) {
        if (!droneSettings.playersEnabled()) {
            droneManager.sendMessage(sender, "players-disabled");
            return true;
        }
        if (!sender.hasPermission("drone.send.players")) {
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
        if (blacklistRepository.isPlayerBlacklisted(target.getUniqueId(), sender.getUniqueId())) {
            droneManager.sendMessage(sender, "blacklist-player-blocked", "<player>", target.getName());
            return true;
        }
        if (!droneManager.canSenderLaunch(sender.getUniqueId())) {
            droneManager.sendMessage(sender, "sender-limit-reached", "<max>", String.valueOf(droneManager.maxActiveForSender(sender.getUniqueId())));
            return true;
        }

        // Check if sending to self is allowed
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            if (!plugin.getConfig().getBoolean("settings.drone.allow-send-to-self-player", false)) {
                droneManager.sendMessage(sender, "cannot-send-to-self-player");
                return true;
            }
        }

        // Check player send cooldown
        int playerCooldownSeconds = plugin.getConfig().getInt("settings.drone.send-cooldown-seconds-player", 0);
        if (playerCooldownSeconds > 0) {
            long remainingCooldown = settingsRepository.getRemainingCooldown(sender.getUniqueId(), playerCooldownSeconds);
            if (remainingCooldown > 0) {
                droneManager.sendMessage(sender, "cooldown-active", "<seconds>", String.valueOf(remainingCooldown));
                return true;
            }
        }

        prepareSendFlow(sender, target, null);
        return true;
    }

    private boolean executeAdmin(Player player, String[] args) {
        if (!player.hasPermission("drone.admin.send")) {
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
            droneManager.sendMessage(player, "sender-limit-reached", "<max>", String.valueOf(droneManager.maxActiveForSender(player.getUniqueId())));
            return true;
        }

        // Check player send cooldown (Admin sends are also subject to cooldown)
        int playerCooldownSeconds = plugin.getConfig().getInt("settings.drone.send-cooldown-seconds-player", 0);
        if (playerCooldownSeconds > 0) {
            long remainingCooldown = settingsRepository.getRemainingCooldown(player.getUniqueId(), playerCooldownSeconds);
            if (remainingCooldown > 0) {
                droneManager.sendMessage(player, "cooldown-active", "<seconds>", String.valueOf(remainingCooldown));
                return true;
            }
        }

        prepareSendFlow(player, player, targetLocation);
        return true;
    }

    private boolean executeToggle(Player player) {
        if (!player.hasPermission("drone.toggle")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        boolean current = settingsRepository.canReceive(player.getUniqueId());
        settingsRepository.setCanReceive(player.getUniqueId(), !current);
        droneManager.sendMessage(player, current ? "toggle-off" : "toggle-on");
        return true;
    }

    private boolean executeReload(Player player) {
        if (!player.hasPermission("drone.admin.reload")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        plugin.reloadPlugin();
        menuHandler.updateSettings(droneManager.settings());
        droneManager.sendMessage(player, "reload");
        return true;
    }

    private boolean executeConfig(Player player) {
        if (!player.hasPermission("drone.admin.config")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        plugin.getConfigEditorHandler().openEditor(player);
        return true;
    }

    private boolean executeConvert(Player player, String[] args) {
        if (!player.hasPermission("drone.admin.convert")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.component("usage-convert"));
            return true;
        }
        
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("yaml-to-mysql")) {
            DataConverter.convertYamlToMysql(plugin, player);
            return true;
        } else if (action.equals("mysql-to-yaml")) {
            DataConverter.convertMysqlToYaml(plugin, player);
            return true;
        } else {
            player.sendMessage(plugin.component("usage-convert"));
            return true;
        }
    }

    
    private boolean executeList(Player player) {
        if (!player.hasPermission("drone.admin.list")) {
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

    private boolean executeBlacklist(Player player, String[] args) {
        if (!player.hasPermission("drone.use")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 2) {
            menuHandler.getMenuGUI().openBlacklistManagementMenu(player);
            return true;
        }

        if (!"player".equalsIgnoreCase(args[1])) {
            player.sendMessage(plugin.component("usage-blacklist"));
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(plugin.component("usage-blacklist"));
            return true;
        }

        String action = args[2].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "add" -> executePlayerBlacklistAdd(player, args);
            case "remove" -> executePlayerBlacklistRemove(player, args);
            case "list" -> executePlayerBlacklistList(player);
            default -> {
                player.sendMessage(plugin.component("usage-blacklist"));
                yield true;
            }
        };
    }

    private boolean executePlayerBlacklistAdd(Player player, String[] args) {
        if (!player.hasPermission("drone.blacklist.player.add")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 4) {
            menuHandler.getMenuGUI().openPlayerBlacklistSelectionMenu(player, true);
            return true;
        }

        UUID targetId = resolvePlayerUuid(args[3]);
        if (targetId == null) {
            droneManager.sendMessage(player, "player-never-played", "<player>", args[3]);
            return true;
        }
        if (targetId.equals(player.getUniqueId())) {
            droneManager.sendMessage(player, "blacklist-self");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String targetName = target.getName() != null ? target.getName() : args[3];

        if (blacklistRepository.addToPlayerBlacklist(player.getUniqueId(), targetId)) {
            droneManager.sendMessage(player, "blacklist-player-added", "<player>", targetName);
        } else {
            droneManager.sendMessage(player, "blacklist-player-already", "<player>", targetName);
        }
        return true;
    }

    private boolean executePlayerBlacklistRemove(Player player, String[] args) {
        if (!player.hasPermission("drone.blacklist.player.remove")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 4) {
            menuHandler.getMenuGUI().openPlayerBlacklistSelectionMenu(player, false);
            return true;
        }

        UUID targetId = resolvePlayerUuid(args[3]);
        if (targetId == null) {
            droneManager.sendMessage(player, "player-never-played", "<player>", args[3]);
            return true;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetId);
        String targetName = offline.getName() != null ? offline.getName() : args[3];

        if (blacklistRepository.removeFromPlayerBlacklist(player.getUniqueId(), targetId)) {
            droneManager.sendMessage(player, "blacklist-player-removed", "<player>", targetName);
        } else {
            droneManager.sendMessage(player, "blacklist-player-not-found", "<player>", targetName);
        }
        return true;
    }

    private boolean executePlayerBlacklistList(Player player) {
        if (!player.hasPermission("drone.blacklist.player.list")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        List<UUID> entries = blacklistRepository.getPlayerBlacklist(player.getUniqueId());

        if (entries.isEmpty()) {
            droneManager.sendMessage(player, "blacklist-list-empty");
            return true;
        }

        droneManager.sendMessage(player, "blacklist-player-list-header", "<count>", String.valueOf(entries.size()));
        for (UUID entryId : entries) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entryId);
            String name = offline.getName() != null ? offline.getName() : entryId.toString();
            player.sendMessage(plugin.componentMessage("blacklist-list-entry", "<player>", name));
        }
        return true;
    }

    private UUID resolvePlayerUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getUniqueId();
        }
        return null;
    }

    private boolean executeDecline(Player player) {
        if (!player.hasPermission("drone.decline")) {
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

    private boolean executeCancel(Player player) {
        if (!player.hasPermission("drone.cancel")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        int cancelled = droneManager.cancelOutgoing(player);
        if (cancelled <= 0) {
            droneManager.sendMessage(player, "cancel-none");
            return true;
        }
        droneManager.sendMessage(player, "cancel-success", "<count>", String.valueOf(cancelled));
        return true;
    }

    private boolean executeLocate(Player player) {
        if (!player.hasPermission("drone.locate")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        java.util.List<de.cb.drones.drone.DeliveryDrone> landedDrones = new ArrayList<>();
        for (de.cb.drones.drone.DeliveryDrone drone : droneManager.activeDronesSnapshot()) {
            if (drone.receiverId().equals(player.getUniqueId()) && drone.isLanded() && drone.currentLocation().getWorld().equals(player.getWorld())) {
                landedDrones.add(drone);
            }
        }
        if (landedDrones.isEmpty()) {
            droneManager.sendMessage(player, "locate-none");
            return true;
        }
        
        de.cb.drones.drone.DeliveryDrone targetDrone = null;
        double minDistanceSq = Double.MAX_VALUE;
        for (de.cb.drones.drone.DeliveryDrone drone : landedDrones) {
            double distSq = drone.currentLocation().distanceSquared(player.getLocation());
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq;
                targetDrone = drone;
            }
        }
        
        final de.cb.drones.drone.DeliveryDrone finalTargetDrone = targetDrone;
        
        droneManager.sendMessage(player, "locate-started");
        
        new org.bukkit.scheduler.BukkitRunnable() {
            int runs = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !droneManager.activeDronesSnapshot().contains(finalTargetDrone) 
                        || !finalTargetDrone.isLanded() || !finalTargetDrone.currentLocation().getWorld().equals(player.getWorld())) {
                    cancel();
                    return;
                }
                
                java.util.List<Location> path = findPath(player.getLocation(), finalTargetDrone.currentLocation());
                if (path.isEmpty()) {
                    Location from = player.getLocation().add(0, 0.2, 0);
                    Location to = finalTargetDrone.currentLocation().add(0, 0.5, 0);
                    org.bukkit.util.Vector dir = to.toVector().subtract(from.toVector());
                    double dist = dir.length();
                    if (dist > 1.5) {
                        dir.normalize();
                        for (double d = 1.0; d < dist; d += 1.5) {
                            Location loc = from.clone().add(dir.clone().multiply(d));
                            spawnLocateParticle(player, loc);
                        }
                    }
                } else {
                    for (int i = 0; i < path.size() - 1; i++) {
                        Location from = path.get(i);
                        Location to = path.get(i + 1);
                        org.bukkit.util.Vector dir = to.toVector().subtract(from.toVector());
                        double dist = dir.length();
                        if (dist > 0.1) {
                            dir.normalize();
                            for (double d = 0.0; d < dist; d += 1.0) {
                                Location loc = from.clone().add(dir.clone().multiply(d));
                                spawnLocateParticle(player, loc);
                            }
                        }
                    }
                    spawnLocateParticle(player, path.get(path.size() - 1));
                }
                
                runs++;
                if (runs >= 20) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
        
        return true;
    }

    private void spawnLocateParticle(Player player, Location loc) {
        DroneSettings.ParticleEffect pe = droneSettings.locateParticle();
        if (pe != null && pe.particle() != null) {
            if (pe.data() != null) {
                player.spawnParticle(pe.particle(), loc, 1, 0.0, 0.0, 0.0, 0.0, pe.data());
            } else {
                player.spawnParticle(pe.particle(), loc, 1, 0.0, 0.0, 0.0, 0.0);
            }
        } else {
            player.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private boolean executeSocket(Player player, String[] args) {
        if (!droneSettings.socketsEnabled()) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
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
            case "blacklist" -> executeSocketBlacklist(player, args);
            default -> {
                player.sendMessage(plugin.component("usage-socket"));
                yield true;
            }
        };
    }

    private boolean isValidSocketName(String name) {
        if (droneSettings.socketNameUseAllowedList()) {
            for (char c : name.toCharArray()) {
                if (droneSettings.socketNameAllowedChars().indexOf(c) == -1) {
                    return false;
                }
            }
            return true;
        } else {
            for (char c : name.toCharArray()) {
                if (droneSettings.socketNameProhibitedChars().indexOf(c) != -1) {
                    return false;
                }
            }
            return true;
        }
    }

    private boolean executeSocketPlace(Player player, String[] args) {
        if (!player.hasPermission("drone.socket.place")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(plugin.component("usage-socket-place"));
            return true;
        }
        String socketName = args[2];
        if (socketName.length() > 32) {
            droneManager.sendMessage(player, "socket-name-too-long");
            return true;
        }
        if (!isValidSocketName(socketName)) {
            droneManager.sendMessage(player, "socket-name-invalid");
            return true;
        }

        if (socketRepository.socketNameExistsGlobally(socketName)) {
            droneManager.sendMessage(player, "socket-exists", "<name>", socketName);
            return true;
        }

        if (droneManager.isBlockedWorld(player.getWorld().getName())) {
            droneManager.sendMessage(player, "world-blocked", "<world>", player.getWorld().getName());
            return true;
        }

        Location loc = player.getLocation();
        try {
            socketRepository.addSocket(player.getUniqueId(), player.getName(), socketName, loc, droneManager.maxSocketsFor(player));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Maximum") && e.getMessage().contains("socket(s) per player")) {
                droneManager.sendMessage(player, "socket-limit-reached", "<max>", String.valueOf(droneManager.maxSocketsFor(player)));
                return true;
            }
            throw e;
        }

        droneManager.sendMessage(player, "socket-placed", "<name>", socketName);
        droneManager.sendMessage(player, "socket-location", "<coords>", loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        return true;
    }

    private boolean executeSocketRemove(Player player, String[] args) {
        if (!player.hasPermission("drone.socket.remove")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
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
        if (!player.hasPermission("drone.socket.list")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
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
        if (!player.hasPermission("drone.socket.manage")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        menuHandler.getMenuGUI().openSocketManagementMenu(player);
        return true;
    }

    private boolean executeSocketRename(Player player, String[] args) {
        if (!player.hasPermission("drone.socket.rename")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 4) {
            player.sendMessage(plugin.component("usage-socket-rename"));
            return true;
        }
        String oldName = args[2];
        String newName = args[3];
        
        if (newName.length() > 32) {
            droneManager.sendMessage(player, "socket-name-too-long");
            return true;
        }
        if (!isValidSocketName(newName)) {
            droneManager.sendMessage(player, "socket-name-invalid");
            return true;
        }

        DeliverySocket socket = socketRepository.getSocket(player.getUniqueId(), oldName);
        if (socket == null) {
            droneManager.sendMessage(player, "socket-not-found", "<name>", oldName);
            return true;
        }

        if (socketRepository.socketNameExistsGlobally(newName) && !newName.equalsIgnoreCase(oldName)) {
            droneManager.sendMessage(player, "socket-exists", "<name>", newName);
            return true;
        }

        if (!socketRepository.renameSocket(player.getUniqueId(), oldName, newName)) {
            droneManager.sendMessage(player, "socket-exists", "<name>", newName);
            return true;
        }
        droneManager.sendMessage(player, "socket-renamed", "<old>", oldName, "<new>", newName);
        return true;
    }

    private boolean executeSocketTrust(Player player, String[] args) {
        if (!player.hasPermission("drone.socket.trust")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 4) {
            player.sendMessage(plugin.component("usage-socket-trust"));
            return true;
        }
        String socketName = args[2];
        String targetPlayerName = args[3];

        DeliverySocket socket = socketRepository.getSocket(player.getUniqueId(), socketName);
        if (socket == null) {
            droneManager.sendMessage(player, "socket-not-found", "<name>", socketName);
            return true;
        }

        UUID targetId = resolvePlayerUuid(targetPlayerName);
        if (targetId == null) {
            droneManager.sendMessage(player, "player-never-played", "<player>", targetPlayerName);
            return true;
        }

        if (socketRepository.addTrustedPlayer(player.getUniqueId(), socketName, targetId)) {
            droneManager.sendMessage(player, "socket-trust-added", "<socket>", socketName, "<player>", targetPlayerName);
        } else {
            droneManager.sendMessage(player, "socket-trust-already", "<socket>", socketName, "<player>", targetPlayerName);
        }
        return true;
    }

    private boolean executeSocketUntrust(Player player, String[] args) {
        if (!player.hasPermission("drone.socket.untrust")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 4) {
            player.sendMessage(plugin.component("usage-socket-untrust"));
            return true;
        }
        String socketName = args[2];
        String targetPlayerName = args[3];

        DeliverySocket socket = socketRepository.getSocket(player.getUniqueId(), socketName);
        if (socket == null) {
            droneManager.sendMessage(player, "socket-not-found", "<name>", socketName);
            return true;
        }

        UUID targetId = resolvePlayerUuid(targetPlayerName);
        if (targetId == null) {
            droneManager.sendMessage(player, "player-never-played", "<player>", targetPlayerName);
            return true;
        }

        if (socketRepository.removeTrustedPlayer(player.getUniqueId(), socketName, targetId)) {
            droneManager.sendMessage(player, "socket-trust-removed", "<socket>", socketName, "<player>", targetPlayerName);
        } else {
            droneManager.sendMessage(player, "socket-trust-not-found", "<socket>", socketName, "<player>", targetPlayerName);
        }
        return true;
    }

    private boolean executeSocketBlacklist(Player player, String[] args) {
        if (!player.hasPermission("drone.socket.blacklist")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 4) {
            player.sendMessage(plugin.component("usage-socket-blacklist"));
            return true;
        }

        String socketName = args[2];
        if (!socketRepository.socketNameExists(player.getUniqueId(), socketName)) {
            droneManager.sendMessage(player, "socket-not-found", "<name>", socketName);
            return true;
        }

        String action = args[3].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "add" -> executeSocketBlacklistAdd(player, socketName, args);
            case "remove" -> executeSocketBlacklistRemove(player, socketName, args);
            case "list" -> executeSocketBlacklistList(player, socketName);
            default -> {
                player.sendMessage(plugin.component("usage-socket-blacklist"));
                yield true;
            }
        };
    }

    private boolean executeSocketBlacklistAdd(Player player, String socketName, String[] args) {
        if (args.length < 5) {
            menuHandler.getMenuGUI().openSocketBlacklistSelectionMenu(player, socketName, true);
            return true;
        }

        UUID targetId = resolvePlayerUuid(args[4]);
        if (targetId == null) {
            droneManager.sendMessage(player, "player-never-played", "<player>", args[4]);
            return true;
        }
        if (targetId.equals(player.getUniqueId())) {
            droneManager.sendMessage(player, "blacklist-self");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String targetName = target.getName() != null ? target.getName() : args[4];

        if (socketRepository.addBlacklistedPlayer(player.getUniqueId(), socketName, targetId)) {
            droneManager.sendMessage(player, "blacklist-socket-added", "<player>", targetName, "<socket>", socketName);
        } else {
            droneManager.sendMessage(player, "blacklist-socket-already", "<player>", targetName, "<socket>", socketName);
        }
        return true;
    }

    private boolean executeSocketBlacklistRemove(Player player, String socketName, String[] args) {
        if (args.length < 5) {
            menuHandler.getMenuGUI().openSocketBlacklistSelectionMenu(player, socketName, false);
            return true;
        }

        UUID targetId = resolvePlayerUuid(args[4]);
        if (targetId == null) {
            droneManager.sendMessage(player, "player-never-played", "<player>", args[4]);
            return true;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetId);
        String targetName = offline.getName() != null ? offline.getName() : args[4];

        if (socketRepository.removeBlacklistedPlayer(player.getUniqueId(), socketName, targetId)) {
            droneManager.sendMessage(player, "blacklist-socket-removed", "<player>", targetName, "<socket>", socketName);
        } else {
            droneManager.sendMessage(player, "blacklist-socket-not-found", "<player>", targetName, "<socket>", socketName);
        }
        return true;
    }

    private boolean executeSocketBlacklistList(Player player, String socketName) {
        List<UUID> entries = socketRepository.getBlacklistedPlayers(player.getUniqueId(), socketName);
        if (entries.isEmpty()) {
            droneManager.sendMessage(player, "blacklist-list-empty");
            return true;
        }

        droneManager.sendMessage(player, "blacklist-socket-list-header", "<socket>", socketName, "<count>", String.valueOf(entries.size()));
        for (UUID entryId : entries) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entryId);
            String name = offline.getName() != null ? offline.getName() : entryId.toString();
            player.sendMessage(plugin.componentMessage("blacklist-list-entry", "<player>", name));
        }
        return true;
    }

    private boolean executeSocketSend(Player player, String[] args) {
        if (!player.hasPermission("drone.socket.send")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(plugin.component("usage-socket-send"));
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

        if (socketRepository.isBlacklisted(targetSocket.ownerId(), socketName, player.getUniqueId())) {
            droneManager.sendMessage(player, "blacklist-socket-blocked", "<socket>", socketName);
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
            droneManager.sendMessage(player, "sender-limit-reached", "<max>", String.valueOf(droneManager.maxActiveForSender(player.getUniqueId())));
            return true;
        }

        // Check if sending to own socket is allowed
        if (player.getUniqueId().equals(targetSocket.ownerId())) {
            if (!plugin.getConfig().getBoolean("settings.drone.allow-send-to-self-socket", false)) {
                droneManager.sendMessage(player, "cannot-send-to-self-socket");
                return true;
            }
        }

        // Check socket send cooldown
        int socketCooldownSeconds = plugin.getConfig().getInt("settings.drone.send-cooldown-seconds-socket", 0);
        if (socketCooldownSeconds > 0) {
            long remainingCooldown = socketRepository.getRemainingCooldown(targetSocket.socketId(), socketCooldownSeconds);
            if (remainingCooldown > 0) {
                droneManager.sendMessage(player, "cooldown-active", "<seconds>", String.valueOf(remainingCooldown));
                return true;
            }
        }

        prepareSendFlow(player, socketOwner, targetSocket.location(), true, targetSocket.name());
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
        if (suppressComposeHubReopen.remove(sender.getUniqueId())) {
            return;
        }
        ItemStack[] contents = snapshotComposeContents(inv);
        inv.clear();
        boolean animalsOnly = composeHubAnimalsOnly.getOrDefault(sender.getUniqueId(), false);
        PendingSendDraft draft = new PendingSendDraft(
                holder.senderId(),
                holder.receiverId(),
                holder.fixedTarget(),
                holder.adminSend(),
                holder.selectedAnimalIds(),
                holder.exactSocketTarget(),
                holder.socketName(),
                contents,
                animalsOnly
        );
        storeComposeDraftInMemory(sender.getUniqueId(), draft, animalsOnly);
        scheduleComposeHubReopen(sender, holder);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onComposeHubClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player sender)) {
            return;
        }
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof ComposeHubInventoryHolder holder)) {
            return;
        }
        if (!holder.senderId().equals(sender.getUniqueId())) {
            return;
        }
        saveComposeHubStateToMemory(sender, holder);
    }

    private void handleLegacyComposeClose(Player sender, ComposeInventoryHolder holder, Inventory inv) {
        if (holder.animalsOnly()) {
            inv.clear();
            Inventory deliveryInventory = Bukkit.createInventory(
                    new DroneInventoryHolder(holder.senderId(), holder.receiverId()),
                    droneManager.settings().inventorySize(),
                    MINI_MESSAGE.deserialize(plugin.getLanguageManager().getString("drone-inventory-title", "<gold>Delivery Drone</gold>"))
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
                MINI_MESSAGE.deserialize(plugin.getLanguageManager().getString("drone-inventory-title", "<gold>Delivery Drone</gold>"))
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
        boolean requiresLeash = !droneManager.settings().animalSelectionEnabled();
        List<String> blacklist = droneManager.settings().mobSendingBlacklist();
        for (UUID animalId : selectedAnimalIds) {
            Entity entity = Bukkit.getEntity(animalId);
            if (!(entity instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            // Skip blacklisted mob types
            if (blacklist.contains(entity.getType().name().toUpperCase())) {
                continue;
            }
            if (requiresLeash) {
                if (!living.isLeashed()) {
                    continue;
                }
                Entity leashHolder = living.getLeashHolder();
                if (leashHolder != null && leashHolder.getUniqueId().equals(senderId)) {
                    selected.add(living);
                }
            } else {
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
        if (!player.hasPermission("drone.preview")) {
            droneManager.sendMessage(player, "no-permission");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.component("usage-preview"));
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
        int previewSize = drone.animalsOnlyDelivery()
                ? droneManager.settings().guiConfig().sendMode().size()
                : drone.inventory().getSize();
        Inventory preview = Bukkit.createInventory(
                new PreviewInventoryHolder(drone.droneId(), player.getUniqueId()),
                previewSize,
                MINI_MESSAGE.deserialize(plugin.getLanguageManager().getString("drone-preview-title", "<gold>Drone Vorschau</gold>"))
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
                String animalName = plugin.getLanguageManager().getString("animal-display-name", "<gold>Tier: <type></gold>")
                    .replace("<type>", entry.getKey().name());
                String animalCount = plugin.getLanguageManager().getString("animal-display-count", "<gray>Anzahl: <count></gray>")
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
            List<String> commands = new ArrayList<>(List.of("admin", "preview", "toggle", "reload", "list", "decline", "cancel", "convert", "config"));
            if (droneSettings.playersEnabled()) {
                commands.add("send");
                commands.add("blacklist");
            }
            if (droneSettings.socketsEnabled()) {
                commands.add("socket");
            }
            if (droneSettings.locateParticlesEnabled()) {
                commands.add("locate");
            }
            return commands;
        }
        if (args.length == 2 && "convert".equalsIgnoreCase(args[0])) {
            return List.of("yaml-to-mysql", "mysql-to-yaml");
        }
        if (args.length == 2 && "blacklist".equalsIgnoreCase(args[0])) {
            if (!droneSettings.playersEnabled()) return List.of();
            return List.of("player");
        }
        if (args.length == 3 && "blacklist".equalsIgnoreCase(args[0]) && "player".equalsIgnoreCase(args[1])) {
            if (!droneSettings.playersEnabled()) return List.of();
            return List.of("add", "remove", "list");
        }
        if (args.length == 4 && "blacklist".equalsIgnoreCase(args[0]) && "player".equalsIgnoreCase(args[1]) && sender instanceof Player player) {
            if (!droneSettings.playersEnabled()) return List.of();
            if ("add".equalsIgnoreCase(args[2])) {
                return getAllPlayedBeforePlayerNames(player);
            }
            if ("remove".equalsIgnoreCase(args[2])) {
                List<String> results = new ArrayList<>();
                for (UUID entryId : blacklistRepository.getPlayerBlacklist(player.getUniqueId())) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(entryId);
                    if (offline.getName() != null) {
                        results.add(offline.getName());
                    }
                }
                return results;
            }
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
        if (args.length == 2 && "send".equalsIgnoreCase(args[0]) && sender instanceof Player player) {
            if (!droneSettings.playersEnabled()) return List.of();
            return getSendableOnlinePlayerNames(player);
        }
        if (args.length == 2 && "socket".equalsIgnoreCase(args[0])) {
            return List.of("place", "remove", "list", "send", "manage", "rename", "trust", "untrust", "blacklist");
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
                if ("trust".equalsIgnoreCase(args[1]) || "untrust".equalsIgnoreCase(args[1]) || "blacklist".equalsIgnoreCase(args[1])) {
                    return socketRepository.getSocketsByOwner(player.getUniqueId()).stream()
                            .map(DeliverySocket::name)
                            .toList();
                }
            }
        }
        if (args.length == 4 && "socket".equalsIgnoreCase(args[0]) && "blacklist".equalsIgnoreCase(args[1]) && sender instanceof Player player) {
            return List.of("add", "remove", "list");
        }
        if (args.length == 5 && "socket".equalsIgnoreCase(args[0]) && "blacklist".equalsIgnoreCase(args[1]) && sender instanceof Player player) {
            if ("add".equalsIgnoreCase(args[3])) {
                return getAllPlayedBeforePlayerNames(player);
            }
            if ("remove".equalsIgnoreCase(args[3])) {
                List<String> results = new ArrayList<>();
                for (UUID entryId : socketRepository.getBlacklistedPlayers(player.getUniqueId(), args[2])) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(entryId);
                    if (offline.getName() != null) {
                        results.add(offline.getName());
                    }
                }
                return results;
            }
        }
        if (args.length == 4 && "socket".equalsIgnoreCase(args[0]) && sender instanceof Player player) {
            if ("trust".equalsIgnoreCase(args[1])) {
                return getAllPlayedBeforePlayerNames(player);
            }
            if ("untrust".equalsIgnoreCase(args[1])) {
                de.cb.drones.socket.DeliverySocket socket = socketRepository.getSocket(player.getUniqueId(), args[2]);
                if (socket == null) return Collections.emptyList();
                List<String> results = new ArrayList<>();
                for (UUID trusted : socket.trustedPlayers()) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(trusted);
                    if (offline.getName() != null) {
                        results.add(offline.getName());
                    }
                }
                return results;
            }
        }
        return Collections.emptyList();
    }

    private List<String> getAllPlayedBeforePlayerNames(Player sender) {
        List<String> results = new ArrayList<>();
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.hasPlayedBefore() || offline.isOnline()) {
                if (offline.getName() != null && !offline.getUniqueId().equals(sender.getUniqueId())) {
                    results.add(offline.getName());
                }
            }
        }
        return results;
    }

    private List<String> getSendableOnlinePlayerNames(Player sender) {
        List<String> results = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(sender)) {
                continue;
            }
            if (!settingsRepository.canReceive(target.getUniqueId())) {
                continue;
            }
            if (blacklistRepository.isPlayerBlacklisted(target.getUniqueId(), sender.getUniqueId())) {
                continue;
            }
            results.add(target.getName());
        }
        return results;
    }


    @EventHandler
    public void onPreviewClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewInventoryHolder) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof ComposeHubInventoryHolder hubHolder) {
            handleComposeHubClick(event, hubHolder);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof SendModeInventoryHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
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

        var sendModeItems = droneManager.settings().guiConfig().sendMode().items();
        int slot = event.getSlot();
        GuiItem animalsItem = sendModeItems.get("animals");
        GuiItem itemsItem = sendModeItems.get("items");

        if (animalsItem != null && slot == animalsItem.position()) {
            Bukkit.getScheduler().runTask(plugin, () -> sendAnimalsOnly(sender, holder));
            return;
        }
        if (itemsItem != null && slot == itemsItem.position()) {
            openComposeHubFromSendMode(sender, holder);
        }
    }

    private void handleComposeHubClick(InventoryClickEvent event, ComposeHubInventoryHolder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
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
        GuiSettings hub = droneManager.settings().guiConfig().composeHub();
        int slot = event.getSlot();
        GuiItem loadItem = hub.items().get("load-items");
        GuiItem launchItem = hub.items().get("launch");
        GuiItem animalsItem = hub.items().get("send-animals");
        if (loadItem != null && slot == loadItem.position()) {
            Bukkit.getScheduler().runTask(plugin, () -> openComposeInventory(sender, holder));
            return;
        }
        if (animalsItem != null && slot == animalsItem.position()) {
            if (droneManager.settings().animalSelectionEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    de.cb.drones.gui.AnimalSelectionGUI gui = new de.cb.drones.gui.AnimalSelectionGUI(plugin, droneManager, sender, holder);
                    gui.openSelectionMenu();
                });
            }
            return;
        }
        if (launchItem != null && slot == launchItem.position()) {
            Bukkit.getScheduler().runTask(plugin, () -> launchDroneFromComposeHub(sender, holder));
        }
    }

    private void toggleComposeHubAnimalsOnly(Player sender, ComposeHubInventoryHolder holder) {
        boolean enabled = !holder.animalsOnlyMode();
        composeHubAnimalsOnly.put(sender.getUniqueId(), enabled);
        boolean composeInventoryOpen = sender.getOpenInventory().getTopInventory().getHolder()
                instanceof ComposeInventoryHolder;
        if (enabled) {
            returnComposeDraftItemsToPlayer(sender);
            if (composeInventoryOpen) {
                suppressComposeHubReopen.add(sender.getUniqueId());
            }
            PendingSendDraft emptyDraft = new PendingSendDraft(
                    holder.senderId(),
                    holder.receiverId(),
                    holder.fixedTarget(),
                    holder.adminSend(),
                    holder.selectedAnimalIds(),
                    holder.exactSocketTarget(),
                    holder.socketName(),
                    new ItemStack[droneManager.settings().inventorySize()],
                    true
            );
            storeComposeDraftInMemory(sender.getUniqueId(), emptyDraft, true);
            droneManager.sendMessage(sender, "compose-hub-animals-only-on");
        } else {
            PendingSendDraft draft = sendDrafts.get(sender.getUniqueId());
            if (draft != null) {
                PendingSendDraft updated = new PendingSendDraft(
                        draft.senderId(),
                        draft.receiverId(),
                        draft.fixedTarget(),
                        draft.adminSend(),
                        draft.selectedAnimalIds(),
                        draft.exactSocketTarget(),
                        draft.socketName(),
                        draft.contents(),
                        false
                );
                storeComposeDraftInMemory(sender.getUniqueId(), updated, false);
            } else {
                composeHubAnimalsOnly.put(sender.getUniqueId(), false);
            }
            droneManager.sendMessage(sender, "compose-hub-animals-only-off");
        }
        Player receiver = Bukkit.getPlayer(holder.receiverId());
        if (receiver == null || !receiver.isOnline()) {
            droneManager.sendMessage(sender, "player-offline");
            suppressComposeHubReopen.remove(sender.getUniqueId());
            return;
        }
        openComposeHub(
                sender,
                receiver,
                holder.fixedTarget(),
                holder.exactSocketTarget(),
                holder.socketName(),
                holder.selectedAnimalIds(),
                enabled
        );
        suppressComposeHubReopen.remove(sender.getUniqueId());
    }

    public void reopenComposeHub(Player sender, ComposeHubInventoryHolder holder) {
        Player receiver = Bukkit.getPlayer(holder.receiverId());
        if (receiver == null || !receiver.isOnline()) {
            droneManager.sendMessage(sender, "player-offline");
            return;
        }
        openComposeHub(sender, receiver, holder.fixedTarget(), holder.exactSocketTarget(), holder.socketName(), holder.selectedAnimalIds(), holder.animalsOnlyMode());
    }

    public void finishAnimalSelectionLaunch(Player sender, ComposeHubInventoryHolder hubHolder, List<UUID> finalSelection) {
        composeHubAnimalsOnly.put(sender.getUniqueId(), true);
        returnComposeDraftItemsToPlayer(sender);
        
        PendingSendDraft emptyDraft = new PendingSendDraft(
                hubHolder.senderId(),
                hubHolder.receiverId(),
                hubHolder.fixedTarget(),
                hubHolder.adminSend(),
                finalSelection,
                hubHolder.exactSocketTarget(),
                hubHolder.socketName(),
                new ItemStack[droneManager.settings().inventorySize()],
                true
        );
        storeComposeDraftInMemory(sender.getUniqueId(), emptyDraft, true);
        
        ComposeHubInventoryHolder updatedHolder = new ComposeHubInventoryHolder(
                hubHolder.senderId(),
                hubHolder.receiverId(),
                hubHolder.fixedTarget(),
                hubHolder.adminSend(),
                finalSelection,
                hubHolder.exactSocketTarget(),
                hubHolder.socketName(),
                true
        );
        
        openComposeHub(
                sender,
                Bukkit.getPlayer(updatedHolder.receiverId()),
                updatedHolder.fixedTarget(),
                updatedHolder.exactSocketTarget(),
                updatedHolder.socketName(),
                updatedHolder.selectedAnimalIds(),
                true
        );
    }

    @EventHandler
    public void onPreviewDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PreviewInventoryHolder
                || event.getView().getTopInventory().getHolder() instanceof SendModeInventoryHolder
                || event.getView().getTopInventory().getHolder() instanceof ComposeHubInventoryHolder) {
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
        if (socketName != null) {
            if (socketRepository.isBlacklisted(receiver.getUniqueId(), socketName, sender.getUniqueId())) {
                droneManager.sendMessage(sender, "blacklist-socket-blocked", "<socket>", socketName);
                return;
            }
        } else if (blacklistRepository.isPlayerBlacklisted(receiver.getUniqueId(), sender.getUniqueId())) {
            droneManager.sendMessage(sender, "blacklist-player-blocked", "<player>", receiver.getName());
            return;
        }

        List<UUID> leashedAnimalIds = List.of();
        if (droneManager.settings().carryLeashedAnimals()) {
            List<LivingEntity> leashedAnimals = listSenderLeashedAnimals(sender);
            if (!leashedAnimals.isEmpty()) {
                int maxAnimals = droneManager.maxLeashedAnimalsFor(sender);
                if (maxAnimals > 0 && leashedAnimals.size() > maxAnimals) {
                    droneManager.sendMessage(sender, "too-many-leashed-animals", "<max>", String.valueOf(maxAnimals));
                    return;
                }
                leashedAnimalIds = leashedAnimals.stream().map(LivingEntity::getUniqueId).toList();
            }
        }
        restoreComposeDraftIfMatching(sender, receiver, fixedTarget, exactSocketTarget, socketName);
        boolean animalsOnly = composeHubAnimalsOnly.getOrDefault(sender.getUniqueId(), false);
        openComposeHub(sender, receiver, fixedTarget, exactSocketTarget, socketName, leashedAnimalIds, animalsOnly);
    }

    private void openSendModeSelector(
            Player sender,
            Player receiver,
            Location fixedTarget,
            boolean exactSocketTarget,
            String socketName,
            List<UUID> leashedAnimalIds
    ) {
        GuiSettings sendMode = droneManager.settings().guiConfig().sendMode();
        boolean adminSend = fixedTarget != null && sender.getUniqueId().equals(receiver.getUniqueId());
        Inventory selector = Bukkit.createInventory(
                new SendModeInventoryHolder(
                        sender.getUniqueId(),
                        receiver.getUniqueId(),
                        fixedTarget,
                        leashedAnimalIds,
                        adminSend,
                        exactSocketTarget,
                        socketName
                ),
                sendMode.size(),
                MINI_MESSAGE.deserialize(sendMode.title())
        );
        if (sendMode.fillItem() != null) {
            ItemStack filler = GuiItemStacks.create(sendMode.fillItem());
            for (int slot = 0; slot < sendMode.size(); slot++) {
                selector.setItem(slot, filler);
            }
        }
        placeSendModeItem(selector, sendMode, "animals");
        placeSendModeItem(selector, sendMode, "items");
        sender.openInventory(selector);
    }

    private void openComposeInventoryDirect(
            Player sender,
            Player receiver,
            Location fixedTarget,
            boolean exactSocketTarget,
            String socketName,
            List<UUID> selectedAnimalIds,
            boolean animalsOnly
    ) {
        int size = droneManager.settings().inventorySize();
        boolean adminSend = fixedTarget != null && sender.getUniqueId().equals(receiver.getUniqueId());
        ComposeInventoryHolder holder = new ComposeInventoryHolder(
                sender.getUniqueId(),
                receiver.getUniqueId(),
                fixedTarget,
                animalsOnly,
                adminSend,
                selectedAnimalIds,
                exactSocketTarget,
                socketName
        );
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

    private void openComposeHubFromSendMode(Player sender, SendModeInventoryHolder sendModeHolder) {
        Player receiver = Bukkit.getPlayer(sendModeHolder.receiverId());
        if (receiver == null || !receiver.isOnline()) {
            droneManager.sendMessage(sender, "player-offline");
            return;
        }
        openComposeInventoryDirect(
                sender,
                receiver,
                sendModeHolder.fixedTarget(),
                sendModeHolder.exactSocketTarget(),
                sendModeHolder.socketName(),
                sendModeHolder.selectedAnimalIds(),
                false
        );
    }

    private void openComposeHub(
            Player sender,
            Player receiver,
            Location fixedTarget,
            boolean exactSocketTarget,
            String socketName,
            List<UUID> selectedAnimalIds
    ) {
        openComposeHub(sender, receiver, fixedTarget, exactSocketTarget, socketName, selectedAnimalIds, false);
    }

    private void openComposeHub(
            Player sender,
            Player receiver,
            Location fixedTarget,
            boolean exactSocketTarget,
            String socketName,
            List<UUID> selectedAnimalIds,
            boolean animalsOnlyMode
    ) {
        if (!droneManager.settings().carryLeashedAnimals()) {
            animalsOnlyMode = false;
        }
        boolean adminSend = fixedTarget != null && sender.getUniqueId().equals(receiver.getUniqueId());
        GuiSettings hub = droneManager.settings().guiConfig().composeHub();
        ComposeHubInventoryHolder holder = new ComposeHubInventoryHolder(
                sender.getUniqueId(),
                receiver.getUniqueId(),
                fixedTarget,
                adminSend,
                selectedAnimalIds == null ? List.of() : selectedAnimalIds,
                exactSocketTarget,
                socketName,
                animalsOnlyMode
        );
        Inventory menu = Bukkit.createInventory(holder, hub.size(), MINI_MESSAGE.deserialize(hub.title()));
        if (hub.fillItem() != null) {
            ItemStack filler = GuiItemStacks.create(hub.fillItem());
            for (int slot = 0; slot < hub.size(); slot++) {
                menu.setItem(slot, filler);
            }
        }
        placeComposeHubButton(menu, "load-items", false, 0);
        if (hub.items().containsKey("send-animals") && droneManager.settings().animalSelectionEnabled()) {
            placeComposeHubButton(menu, "send-animals", true, holder.selectedAnimalIds().size());
        }
        placeComposeHubButton(menu, "launch", animalsOnlyMode, 0);
        sender.openInventory(menu);
    }

    private void placeComposeHubButton(Inventory inventory, String itemKey, boolean animalsOnlyMode, int animalCount) {
        GuiItem item = droneManager.settings().guiConfig().resolveComposeHubItem(itemKey, animalsOnlyMode);
        if (item == null || item.position() < 0 || item.position() >= inventory.getSize()) {
            return;
        }
        ItemStack stack = GuiItemStacks.create(item);
        if ("send-animals".equals(itemKey) && animalsOnlyMode) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    meta.displayName(MINI_MESSAGE.deserialize(item.name().replace("<count>", String.valueOf(animalCount))));
                }
                if (meta.hasLore() && item.lore() != null) {
                    List<Component> newLore = new java.util.ArrayList<>();
                    for (String line : item.lore()) {
                        newLore.add(MINI_MESSAGE.deserialize(line.replace("<count>", String.valueOf(animalCount))));
                    }
                    meta.lore(newLore);
                }
                stack.setItemMeta(meta);
            }
        }
        inventory.setItem(item.position(), stack);
    }

    private void openComposeInventory(Player sender, ComposeHubInventoryHolder hubHolder) {
        if (hubHolder.animalsOnlyMode()) {
            droneManager.sendMessage(sender, "compose-hub-items-disabled");
            return;
        }
        suppressComposeHubReopen.remove(sender.getUniqueId());
        Player receiver = Bukkit.getPlayer(hubHolder.receiverId());
        if (receiver == null || !receiver.isOnline()) {
            droneManager.sendMessage(sender, "player-offline");
            return;
        }
        int size = droneManager.settings().inventorySize();
        ComposeInventoryHolder holder = new ComposeInventoryHolder(
                hubHolder.senderId(),
                hubHolder.receiverId(),
                hubHolder.fixedTarget(),
                false,
                hubHolder.adminSend(),
                hubHolder.selectedAnimalIds(),
                hubHolder.exactSocketTarget(),
                hubHolder.socketName()
        );
        String titleKey = hubHolder.socketName() != null ? "drone-title-socket" : (hubHolder.fixedTarget() == null ? "drone-title-player" : "drone-title-admin");
        String placeholder = hubHolder.socketName() != null ? "<socket>" : "<player>";
        String value = hubHolder.socketName() != null ? hubHolder.socketName() : receiver.getName();
        Component titleComponent = getComponentMessageWithoutPrefix(titleKey, placeholder, value);
        Inventory compose = Bukkit.createInventory(holder, size, titleComponent);
        PendingSendDraft draft = sendDrafts.get(sender.getUniqueId());
        if (draft != null && draft.contents() != null && draft.contents().length == size) {
            compose.setContents(draft.contents());
        }
        sender.openInventory(compose);
        if (hubHolder.socketName() != null) {
            droneManager.sendMessage(sender, "open-inventory-socket", "<socket>", hubHolder.socketName(), "<player>", receiver.getName());
        } else {
            droneManager.sendMessage(sender, "open-inventory", "<player>", receiver.getName());
        }
    }

    private void launchDroneFromComposeHub(Player sender, ComposeHubInventoryHolder hubHolder) {
        boolean animalsOnlyMode = hubHolder.animalsOnlyMode();
        int size = droneManager.settings().inventorySize();
        ItemStack[] contents;
        boolean hasItems;
        if (animalsOnlyMode) {
            contents = new ItemStack[size];
            hasItems = false;
        } else {
            PendingSendDraft draft = sendDrafts.get(sender.getUniqueId());
            contents = draft != null && draft.contents() != null ? draft.contents() : new ItemStack[size];
            hasItems = false;
            for (ItemStack stack : contents) {
                if (stack != null && !stack.getType().isAir()) {
                    hasItems = true;
                    break;
                }
            }
        }
        boolean useLeashed = !droneManager.settings().animalSelectionEnabled();
        List<LivingEntity> nearbyLeashed = animalsOnlyMode && useLeashed ? listSenderLeashedAnimals(sender) : List.of();
        if (animalsOnlyMode && useLeashed) {
            int maxAnimals = droneManager.maxLeashedAnimalsFor(sender);
            if (maxAnimals > 0 && nearbyLeashed.size() > maxAnimals) {
                droneManager.sendMessage(sender, "too-many-leashed-animals", "<max>", String.valueOf(maxAnimals));
                return;
            }
        }
        List<UUID> animalIds = (animalsOnlyMode && useLeashed)
                ? nearbyLeashed.stream().map(LivingEntity::getUniqueId).toList()
                : hubHolder.selectedAnimalIds();
        List<LivingEntity> attachedAnimals = resolveSelectedAnimals(hubHolder.senderId(), animalIds);
        if (animalsOnlyMode) {
            if (attachedAnimals.isEmpty()) {
                droneManager.sendMessage(sender, "compose-hub-no-leashed-animals");
                return;
            }
            if (!useLeashed) {
                int count = attachedAnimals.size();
                int has = 0;
                for (org.bukkit.inventory.ItemStack item : sender.getInventory().getContents()) {
                    if (item != null && item.getType() == org.bukkit.Material.LEAD) {
                        has += item.getAmount();
                    }
                }
                if (has < count) {
                    droneManager.sendMessage(sender, "not-enough-leads", "<count>", String.valueOf(count));
                    return;
                }
                int needed = count;
                org.bukkit.inventory.ItemStack[] invContents = sender.getInventory().getContents();
                for (int i = 0; i < invContents.length; i++) {
                    org.bukkit.inventory.ItemStack item = invContents[i];
                    if (item != null && item.getType() == org.bukkit.Material.LEAD) {
                        if (item.getAmount() <= needed) {
                            needed -= item.getAmount();
                            sender.getInventory().setItem(i, null);
                        } else {
                            item.setAmount(item.getAmount() - needed);
                            needed = 0;
                        }
                        if (needed == 0) break;
                    }
                }
            }
        } else if (!hasItems) {
            droneManager.sendMessage(sender, "compose-hub-empty");
            return;
        }
        boolean animalsOnly = animalsOnlyMode;
        ComposeInventoryHolder composeHolder = new ComposeInventoryHolder(
                hubHolder.senderId(),
                hubHolder.receiverId(),
                hubHolder.fixedTarget(),
                animalsOnly,
                hubHolder.adminSend(),
                animalIds,
                hubHolder.exactSocketTarget(),
                hubHolder.socketName()
        );
        Inventory deliveryInventory = Bukkit.createInventory(
                new DroneInventoryHolder(hubHolder.senderId(), hubHolder.receiverId()),
                size,
                MINI_MESSAGE.deserialize(plugin.getLanguageManager().getString("drone-inventory-title", "<gold>Delivery Drone</gold>"))
        );
        if (animalsOnlyMode) {
            deliveryInventory.clear();
        } else {
            deliveryInventory.setContents(contents);
        }
        sender.closeInventory();
        if (!spawnDroneFromSelection(sender, composeHolder, deliveryInventory)) {
            return;
        }
        clearComposeDraftMemory(sender.getUniqueId());
    }

    private static void placeSendModeItem(Inventory inventory, GuiSettings sendMode, String itemKey) {
        GuiItem item = sendMode.items().get(itemKey);
        if (item == null || item.position() < 0 || item.position() >= sendMode.size()) {
            return;
        }
        inventory.setItem(item.position(), GuiItemStacks.create(item));
    }

    private Component getComponentMessageWithoutPrefix(String key, String placeholder, String value) {
        String body = plugin.getLanguageManager().getString(key, key);
        if (placeholder != null && value != null) {
            body = body.replace(placeholder, value);
        }
        return MINI_MESSAGE.deserialize(body);
    }

    private void sendAnimalsOnly(Player sender, SendModeInventoryHolder holder) {
        Inventory deliveryInventory = Bukkit.createInventory(
                new DroneInventoryHolder(holder.senderId(), holder.receiverId()),
                droneManager.settings().inventorySize(),
                MINI_MESSAGE.deserialize(plugin.getLanguageManager().getString("drone-inventory-title", "<gold>Delivery Drone</gold>"))
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

    private boolean spawnDroneFromSelection(Player sender, ComposeInventoryHolder holder, Inventory deliveryInventory) {
        Player receiver = Bukkit.getPlayer(holder.receiverId());
        if (receiver == null || !receiver.isOnline()) {
            droneManager.sendMessage(sender, "player-offline");
            return false;
        }
        if (holder.socketName() != null) {
            if (socketRepository.isBlacklisted(receiver.getUniqueId(), holder.socketName(), sender.getUniqueId())) {
                droneManager.sendMessage(sender, "blacklist-socket-blocked", "<socket>", holder.socketName());
                return false;
            }
        } else if (blacklistRepository.isPlayerBlacklisted(receiver.getUniqueId(), sender.getUniqueId())) {
            droneManager.sendMessage(sender, "blacklist-player-blocked", "<player>", receiver.getName());
            return false;
        }
        List<LivingEntity> attachedAnimals = holder.animalsOnly()
                ? resolveSelectedAnimals(holder.senderId(), holder.selectedAnimalIds())
                : (holder.selectedAnimalIds().isEmpty()
                ? List.of()
                : resolveSelectedAnimals(holder.senderId(), holder.selectedAnimalIds()));
        if (holder.animalsOnly()) {
            deliveryInventory.clear();
        }
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
            return false;
        }

        // Set cooldown times after successful drone spawn
        int playerCooldownSeconds = plugin.getConfig().getInt("settings.drone.send-cooldown-seconds-player", 0);
        if (playerCooldownSeconds > 0) {
            settingsRepository.setLastSendTime(sender.getUniqueId());
        }

        // Set socket send cooldown if sending from socket
        if (holder.socketName() != null) {
            int socketCooldownSeconds = plugin.getConfig().getInt("settings.drone.send-cooldown-seconds-socket", 0);
            if (socketCooldownSeconds > 0) {
                // Get the socket to set its cooldown
                de.cb.drones.socket.DeliverySocket socket = socketRepository.getSocket(receiver.getUniqueId(), holder.socketName());
                if (socket != null) {
                    socketRepository.setLastSendTime(socket.socketId());
                }
            }
        }

        String targetName = holder.socketName() != null ? holder.socketName() : receiver.getName();
        String prefix = plugin.getLanguageManager().getString("prefix", "");
        String sentSuccess = plugin.getLanguageManager().getString("sent-success", "sent-success").replace("<player>", targetName);
        String cancelBtn = plugin.getLanguageManager().getString("sent-cancel-button", "");
        Component sent = MINI_MESSAGE.deserialize(prefix + sentSuccess + cancelBtn);
        sender.sendMessage(sent);
        String incomingBody = plugin.getLanguageManager().getString("incoming-drone", "incoming-drone").replace("<player>", sender.getName());
        String buttonBody = plugin.getLanguageManager().getString("incoming-drone-preview-button", "incoming-drone-preview-button").replace("<id>", drone.droneId().toString());
        Component incoming = MINI_MESSAGE.deserialize(prefix + incomingBody + buttonBody);
        receiver.sendMessage(incoming);
        return true;
    }

    private void loadPersistedComposeDrafts() {
        for (Map.Entry<UUID, StoredComposeDraft> entry : composeDraftRepository.loadAll().entrySet()) {
            UUID senderId = entry.getKey();
            if (droneManager.activeOutgoingCount(senderId) > 0) {
                composeDraftRepository.delete(senderId);
                continue;
            }
            StoredComposeDraft stored = entry.getValue();
            sendDrafts.put(senderId, fromStoredDraft(senderId, stored));
        }
    }

    private void restoreComposeDraftIfMatching(
            Player sender,
            Player receiver,
            Location fixedTarget,
            boolean exactSocketTarget,
            String socketName
    ) {
        PendingSendDraft draft = sendDrafts.get(sender.getUniqueId());
        if (draft == null) {
            return;
        }
        if (!draft.receiverId().equals(receiver.getUniqueId())) {
            clearComposeDraftMemory(sender.getUniqueId());
            return;
        }
        if (!Objects.equals(draft.socketName(), socketName) || draft.exactSocketTarget() != exactSocketTarget) {
            clearComposeDraftMemory(sender.getUniqueId());
            return;
        }
        if (!locationsMatch(draft.fixedTarget(), fixedTarget)) {
            clearComposeDraftMemory(sender.getUniqueId());
            return;
        }
        boolean currentToggle = composeHubAnimalsOnly.getOrDefault(sender.getUniqueId(), false);
        storeComposeDraftInMemory(sender.getUniqueId(), draft, currentToggle);
    }

    private static boolean locationsMatch(Location stored, Location current) {
        if (stored == null && current == null) {
            return true;
        }
        if (stored == null || current == null) {
            return false;
        }
        if (stored.getWorld() == null || current.getWorld() == null) {
            return false;
        }
        return stored.getWorld().getUID().equals(current.getWorld().getUID())
                && stored.getBlockX() == current.getBlockX()
                && stored.getBlockY() == current.getBlockY()
                && stored.getBlockZ() == current.getBlockZ();
    }

    private void storeComposeDraftInMemory(UUID senderId, PendingSendDraft draft, boolean animalsOnly) {
        sendDrafts.put(senderId, draft);
        composeHubAnimalsOnly.put(senderId, animalsOnly);
    }

    private void clearComposeDraftMemory(UUID senderId) {
        sendDrafts.remove(senderId);
    }

    private void saveComposeHubStateToMemory(Player sender, ComposeHubInventoryHolder holder) {
        boolean animalsOnly = composeHubAnimalsOnly.getOrDefault(sender.getUniqueId(), false);
        int size = droneManager.settings().inventorySize();
        PendingSendDraft existing = sendDrafts.get(sender.getUniqueId());
        ItemStack[] contents;
        if (existing != null && existing.contents() != null && existing.contents().length == size) {
            contents = existing.contents();
        } else {
            contents = new ItemStack[size];
        }
        PendingSendDraft draft = new PendingSendDraft(
                holder.senderId(),
                holder.receiverId(),
                holder.fixedTarget(),
                holder.adminSend(),
                holder.selectedAnimalIds(),
                holder.exactSocketTarget(),
                holder.socketName(),
                contents,
                animalsOnly
        );
        storeComposeDraftInMemory(sender.getUniqueId(), draft, animalsOnly);
    }

    private static StoredComposeDraft toStoredDraft(PendingSendDraft draft, boolean animalsOnly) {
        return new StoredComposeDraft(
                draft.receiverId(),
                draft.fixedTarget(),
                draft.adminSend(),
                draft.exactSocketTarget(),
                draft.socketName(),
                draft.selectedAnimalIds(),
                animalsOnly,
                draft.contents()
        );
    }

    private static PendingSendDraft fromStoredDraft(UUID senderId, StoredComposeDraft stored) {
        return new PendingSendDraft(
                senderId,
                stored.receiverId(),
                stored.fixedTarget(),
                stored.adminSend(),
                stored.selectedAnimalIds(),
                stored.exactSocketTarget(),
                stored.socketName(),
                stored.contents(),
                stored.animalsOnlyMode()
        );
    }

    private void returnComposeDraftItemsToPlayer(Player player) {
        List<ItemStack> items = drainAllPackedItems(player);
        if (items.isEmpty()) {
            return;
        }
        droneManager.giveItemsToPlayer(player, items);
        droneManager.sendMessage(player, "compose-hub-items-returned");
    }

    private List<ItemStack> drainAllPackedItems(Player player) {
        List<ItemStack> items = captureComposeInventoryItems(player);
        PendingSendDraft draft = sendDrafts.remove(player.getUniqueId());
        if (draft != null && draft.contents() != null) {
            for (ItemStack stack : draft.contents()) {
                if (stack != null && !stack.getType().isAir()) {
                    items.add(stack.clone());
                }
            }
        }
        return items;
    }

    private List<ItemStack> captureComposeInventoryItems(Player player) {
        List<ItemStack> items = new ArrayList<>();
        InventoryView view = player.getOpenInventory();
        if (!(view.getTopInventory().getHolder() instanceof ComposeInventoryHolder composeHolder)) {
            return items;
        }
        if (!composeHolder.senderId().equals(player.getUniqueId())) {
            return items;
        }
        Inventory top = view.getTopInventory();
        for (ItemStack stack : top.getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                items.add(stack.clone());
            }
        }
        top.clear();
        return items;
    }

    private static ItemStack[] snapshotComposeContents(Inventory inv) {
        ItemStack[] contents = new ItemStack[inv.getSize()];
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = inv.getItem(slot);
            contents[slot] = stack == null || stack.getType().isAir() ? null : stack.clone();
        }
        return contents;
    }

    private void scheduleComposeHubReopen(Player sender, ComposeInventoryHolder holder) {
        boolean animalsOnly = composeHubAnimalsOnly.getOrDefault(sender.getUniqueId(), false);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sender.getOpenInventory().getTopInventory().getHolder() instanceof ComposeHubInventoryHolder) {
                return;
            }
            Player receiver = Bukkit.getPlayer(holder.receiverId());
            if (receiver == null || !receiver.isOnline()) {
                droneManager.sendMessage(sender, "player-offline");
                return;
            }
            openComposeHub(
                    sender,
                    receiver,
                    holder.fixedTarget(),
                    holder.exactSocketTarget(),
                    holder.socketName(),
                    holder.selectedAnimalIds(),
                    animalsOnly
            );
        });
    }

    private record PendingSendDraft(
            UUID senderId,
            UUID receiverId,
            Location fixedTarget,
            boolean adminSend,
            List<UUID> selectedAnimalIds,
            boolean exactSocketTarget,
            String socketName,
            ItemStack[] contents,
            boolean animalsOnlyMode
    ) {
    }

    public record ComposeHubInventoryHolder(
            UUID senderId,
            UUID receiverId,
            Location fixedTarget,
            boolean adminSend,
            List<UUID> selectedAnimalIds,
            boolean exactSocketTarget,
            String socketName,
            boolean animalsOnlyMode
    ) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
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

    private static class PathNode implements Comparable<PathNode> {
        final Location loc;
        final PathNode parent;
        final double g;
        final double h;

        PathNode(Location loc, PathNode parent, double g, double h) {
            this.loc = loc;
            this.parent = parent;
            this.g = g;
            this.h = h;
        }

        double f() {
            return g + h;
        }

        @Override
        public int compareTo(PathNode o) {
            return Double.compare(this.f(), o.f());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PathNode other) {
                return loc.getBlockX() == other.loc.getBlockX() &&
                       loc.getBlockY() == other.loc.getBlockY() &&
                       loc.getBlockZ() == other.loc.getBlockZ();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (loc.getBlockX() * 31 + loc.getBlockY()) * 31 + loc.getBlockZ();
        }
    }

    private java.util.List<Location> findPath(Location start, Location end) {
        java.util.List<Location> path = new java.util.ArrayList<>();
        if (!start.getWorld().equals(end.getWorld())) {
            return path;
        }

        org.bukkit.World world = start.getWorld();
        java.util.PriorityQueue<PathNode> openSet = new java.util.PriorityQueue<>();
        java.util.Set<String> closedSet = new java.util.HashSet<>();

        Location startBlock = start.getBlock().getLocation();
        Location endBlock = end.getBlock().getLocation();

        openSet.add(new PathNode(startBlock, null, 0, startBlock.distance(endBlock)));

        PathNode targetNode = null;
        int iterations = 0;
        int maxIterations = 800;

        while (!openSet.isEmpty() && iterations++ < maxIterations) {
            PathNode current = openSet.poll();

            if (current.loc.distanceSquared(endBlock) <= 2.25) {
                targetNode = current;
                break;
            }

            String currentKey = current.loc.getBlockX() + "," + current.loc.getBlockY() + "," + current.loc.getBlockZ();
            if (closedSet.contains(currentKey)) {
                continue;
            }
            closedSet.add(currentKey);

            int[][] dirs = {
                {1, 0, 0}, {-1, 0, 0},
                {0, 0, 1}, {0, 0, -1}
            };

            for (int[] dir : dirs) {
                for (int dy = -2; dy <= 1; dy++) {
                    int nx = current.loc.getBlockX() + dir[0];
                    int ny = current.loc.getBlockY() + dy;
                    int nz = current.loc.getBlockZ() + dir[2];

                    Location neighborLoc = new Location(world, nx, ny, nz);

                    org.bukkit.block.Block feetBlock = world.getBlockAt(nx, ny, nz);
                    if (!feetBlock.isPassable()) {
                        continue;
                    }

                    org.bukkit.block.Block headBlock = world.getBlockAt(nx, ny + 1, nz);
                    if (!headBlock.isPassable()) {
                        continue;
                    }

                    if (dy == 1) {
                        org.bukkit.block.Block currentHeadBlock = world.getBlockAt(current.loc.getBlockX(), current.loc.getBlockY() + 2, current.loc.getBlockZ());
                        if (!currentHeadBlock.isPassable()) {
                            continue;
                        }
                    }

                    org.bukkit.block.Block standBlock = world.getBlockAt(nx, ny - 1, nz);
                    if (standBlock.isPassable()) {
                        org.bukkit.Material mat = standBlock.getType();
                        if (mat != org.bukkit.Material.WATER && mat != org.bukkit.Material.LADDER && mat != org.bukkit.Material.VINE) {
                            continue;
                        }
                    }

                    double stepCost = 1.0 + Math.abs(dy) * 0.5;
                    double tentativeG = current.g + stepCost;

                    String neighborKey = nx + "," + ny + "," + nz;
                    if (!closedSet.contains(neighborKey)) {
                        openSet.add(new PathNode(neighborLoc, current, tentativeG, neighborLoc.distance(endBlock)));
                    }
                }
            }
        }

        if (targetNode != null) {
            PathNode curr = targetNode;
            while (curr != null) {
                path.add(0, curr.loc.clone().add(0.5, 0.2, 0.5));
                curr = curr.parent;
            }
        }

        return path;
    }
}
