package de.cb.drones.drone;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.config.SocketPendingReturnsRepository;
import de.cb.drones.discord.DiscordWebhookManager;
import de.cb.drones.util.SendMaxPermissions;
import de.cb.drones.performance.PerformanceOptimizer;
import de.cb.drones.config.DronePersistence;
import de.cb.drones.config.YamlDronePersistence;
import de.cb.drones.config.MysqlDronePersistence;
import de.cb.drones.config.DatabaseManager;
import de.cb.drones.socket.DeliverySocket;
import de.cb.drones.socket.SocketRepository;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class DroneManager {
    private final AdvancedDeliveryDronesPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final DiscordWebhookManager discordWebhookManager;
    private final PerformanceOptimizer performanceOptimizer;
    private final SocketRepository socketRepository;
    private final SocketPendingReturnsRepository socketPendingReturns;
    private final Map<UUID, DeliveryDrone> activeDrones = new HashMap<>();
    private final Map<UUID, DeliveryDrone> byEntityUuid = new HashMap<>();
    private final Map<Inventory, DeliveryDrone> byInventory = new HashMap<>();
    private final Map<UUID, Integer> activeBySender = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>();
    private final Map<UUID, Runnable> pendingAnimalReturnCallbacks = new HashMap<>();
    private DroneSettings settings;
    private BukkitTask cleanupTask;
    private DronePersistence persistence;

    public DroneManager(
            AdvancedDeliveryDronesPlugin plugin,
            DroneSettings settings,
            DiscordWebhookManager discordWebhookManager,
            SocketRepository socketRepository,
            SocketPendingReturnsRepository socketPendingReturns,
            DatabaseManager databaseManager
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.discordWebhookManager = discordWebhookManager;
        this.socketRepository = socketRepository;
        this.socketPendingReturns = socketPendingReturns;
        this.performanceOptimizer = new PerformanceOptimizer(plugin);
        
        if ("MYSQL".equalsIgnoreCase(plugin.getConfig().getString("database.type", "YAML"))) {
            this.persistence = new MysqlDronePersistence(plugin, databaseManager);
        } else {
            this.persistence = new YamlDronePersistence(plugin);
        }
    }

    public AdvancedDeliveryDronesPlugin plugin() {
        return plugin;
    }

    public void updateSettings(DroneSettings settings) {
        this.settings = settings;
        for (DeliveryDrone drone : activeDrones.values()) {
            drone.applySettings(settings, this);
        }
    }

    public void updateDatabaseManager(DatabaseManager databaseManager) {
        if ("MYSQL".equalsIgnoreCase(plugin.getConfig().getString("database.type", "YAML"))) {
            this.persistence = new MysqlDronePersistence(plugin, databaseManager);
        } else {
            this.persistence = new YamlDronePersistence(plugin);
        }
    }

    public DroneSettings settings() {
        return settings;
    }

    public String message(String key, String placeholder, String value) {
        return plugin.message(key, placeholder, value);
    }

    public Component componentMessage(String key, String placeholder, String value) {
        return plugin.componentMessage(key, placeholder, value);
    }

    public void sendMessage(Player player, String key, String placeholder, String value) {
        player.sendMessage(componentMessage(key, placeholder, value));
    }

    public void sendMessage(Player player, String key) {
        player.sendMessage(componentMessage(key, null, null));
    }

    public void sendMessage(Player player, String key, String placeholder1, String value1, String placeholder2, String value2) {
        String prefix = plugin.getLanguageManager().getString("prefix", "");
        String body = plugin.getLanguageManager().getString(key, key);
        body = body.replace(placeholder1, value1).replace(placeholder2, value2);
        player.sendMessage(miniMessage.deserialize(prefix + body));
    }

    public void sendMessage(Player player, String key, String placeholder1, String value1, String placeholder2, String value2, String placeholder3, String value3) {
        String prefix = plugin.getLanguageManager().getString("prefix", "");
        String body = plugin.getLanguageManager().getString(key, key);
        body = body.replace(placeholder1, value1).replace(placeholder2, value2).replace(placeholder3, value3);
        player.sendMessage(miniMessage.deserialize(prefix + body));
    }

    public void start() {
        // Clean up any old drone ArmorStands from previous sessions
        cleanupAllOldDrones();
        
        persistence.loadDrones(this);
        
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpired, 20L, 20L);
    }

    public PerformanceOptimizer getPerformanceOptimizer() {
        return performanceOptimizer;
    }

    public SocketRepository getSocketRepository() {
        return socketRepository;
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        performanceOptimizer.shutdown();
        
        persistence.saveDrones(activeDrones.values());
        
        // Handle restart-safe cleanup for all active drones
        handleRestartSafeCleanup();
        
        activeDrones.clear();
        byEntityUuid.clear();
    }

    public DeliveryDrone spawnDrone(Player sender, Player receiver, Inventory inventory, List<LivingEntity> attachedAnimals, boolean animalsOnlyDelivery) {
        return spawnDrone(sender, receiver, inventory, receiver.getLocation().clone(), attachedAnimals, animalsOnlyDelivery, false, false, null);
    }

    public DeliveryDrone spawnDrone(
            Player sender,
            Player receiver,
            Inventory inventory,
            Location fixedTarget,
            List<LivingEntity> attachedAnimals,
            boolean animalsOnlyDelivery,
            boolean forceTargetChunkLoad,
            boolean exactSocketTarget,
            String socketName
    ) {
        Location start = sender.getLocation().clone().add(0, 2.2, 0);
        loadChunkNow(start);
        loadChunkNow(fixedTarget);
        ArmorStand stand = DeliveryDrone.spawnDroneEntity(start, settings);
        if (stand == null) {
            return null;
        }
        List<EntityType> attachedAnimalTypes = attachedAnimals == null
                ? List.of()
                : attachedAnimals.stream().map(LivingEntity::getType).toList();
        if (attachedAnimals != null) {
            for (LivingEntity animal : attachedAnimals) {
                try {
                    if (animal.isLeashed()) {
                        animal.setLeashHolder(null);
                    }
                } catch (Exception ignored) {
                    // best effort cleanup before virtual transport
                }
                animal.remove();
            }
        }
        UUID id = UUID.randomUUID();
        DeliveryDrone drone = new DeliveryDrone(
                id,
                sender.getUniqueId(),
                receiver.getUniqueId(),
                receiver.getName(),
                fixedTarget,
                inventory,
                attachedAnimalTypes,
                animalsOnlyDelivery,
                forceTargetChunkLoad,
                exactSocketTarget,
                socketName,
                settings,
                stand,
                currentTick()
        );
        activeDrones.put(id, drone);
        byEntityUuid.put(drone.standId(), drone);
        byInventory.put(inventory, drone);
        incrementSenderCounter(sender.getUniqueId());
        drone.startFlight(this);

        // Send Discord notification
        discordWebhookManager.sendDeliveryNotification(sender, receiver, drone);

        return drone;
    }

    public void addLoadedDrone(DeliveryDrone drone) {
        activeDrones.put(drone.droneId(), drone);
        if (drone.standId() != null) {
            byEntityUuid.put(drone.standId(), drone);
        }
        byInventory.put(drone.inventory(), drone);
        incrementSenderCounter(drone.senderId());
    }

    public DeliveryDrone findByEntity(UUID entityId) {
        return byEntityUuid.get(entityId);
    }

    public DeliveryDrone findByDroneId(UUID droneId) {
        return activeDrones.get(droneId);
    }

    public void openDroneInventory(Player player, DeliveryDrone drone) {
        if (!drone.wasOpenedByReceiver()) {
            drone.onReceiverOpened();
            drone.releaseLeashedAnimal();
            decrementSenderCounter(drone.senderId());
            
            // Send Discord notification for successful delivery
            Player sender = Bukkit.getPlayer(drone.senderId());
            if (sender != null) {
                discordWebhookManager.sendDeliveryCompleted(sender, player, drone);
            }
            // Only reset countdown on first opening
            drone.markInteraction(currentTick());
        }
        player.openInventory(drone.inventory());
    }

    public void handleAnimalOnlyInteract(DeliveryDrone drone) {
        if (!drone.wasOpenedByReceiver()) {
            drone.onReceiverOpened();
            decrementSenderCounter(drone.senderId());
            
            // Send Discord notification for successful delivery
            Player receiver = Bukkit.getPlayer(drone.receiverId());
            Player sender = Bukkit.getPlayer(drone.senderId());
            if (sender != null && receiver != null) {
                discordWebhookManager.sendDeliveryCompleted(sender, receiver, drone);
            }
            
            // Send socket pickup notifications if this is a socket delivery
            if (drone.socketName() != null && receiver != null) {
                // Find the socket by searching all sockets for the matching name
                DeliverySocket socket = null;
                for (DeliverySocket s : socketRepository.getAllSockets()) {
                    if (s.name().equals(drone.socketName())) {
                        socket = s;
                        break;
                    }
                }
                if (socket != null) {
                    sendSocketPickupNotifications(receiver, drone, socket);
                }
            }
        }
        drone.releaseLeashedAnimal();
        
        // Give leashes/leads for each transported animal
        deliverLeashes(drone);
        
        // Only reset countdown on first interaction
        if (!drone.wasOpenedByReceiver()) {
            drone.markInteraction(currentTick());
        }
    }

    public void sendSocketPickupNotifications(Player pickupPlayer, DeliveryDrone drone, DeliverySocket socket) {
        // Check if notifications have already been sent to prevent duplicates
        if (drone.areNotificationsSent()) {
            return;
        }
        
        // Notify the original sender that their drone was picked up from a socket
        Player sender = Bukkit.getPlayer(drone.senderId());
        if (sender != null && !sender.getUniqueId().equals(pickupPlayer.getUniqueId())) {
            sendMessage(sender, "socket-drone-pickedup-sender", 
                "<pickup_player>", pickupPlayer.getName(), 
                "<socket_name>", socket.name());
        }
        
        // Notify the socket owner if they're not the one who picked it up
        Player socketOwner = Bukkit.getPlayer(socket.ownerId());
        if (socketOwner != null && !socketOwner.getUniqueId().equals(pickupPlayer.getUniqueId())) {
            sendMessage(socketOwner, "socket-drone-pickedup-owner", 
                "<pickup_player>", pickupPlayer.getName(), 
                "<socket_name>", socket.name(), 
                "<original_sender>", Bukkit.getOfflinePlayer(drone.senderId()).getName());
        }
        
        // Notify the pickup player who sent the drone
        sendMessage(pickupPlayer, "socket-drone-pickedup-picker", 
            "<original_sender>", Bukkit.getOfflinePlayer(drone.senderId()).getName(), 
            "<socket_name>", socket.name());
        
        // Mark notifications as sent to prevent duplicates
        drone.markNotificationsSent();
    }

    private void deliverLeashes(DeliveryDrone drone) {
        if (!drone.animalsOnlyDelivery() || drone.attachedAnimalTypes().isEmpty()) {
            return;
        }
        int leashCount = drone.attachedAnimalTypes().size();
        Location dropLocation = drone.currentLocation();
        if (drone.socketName() != null) {
            for (de.cb.drones.socket.DeliverySocket s : socketRepository.getAllSockets()) {
                if (s.name().equals(drone.socketName())) {
                    dropLocation = s.location();
                    break;
                }
            }
        }
        ItemStack leadItem = new ItemStack(Material.LEAD, leashCount);
        dropLocation.getWorld().dropItemNaturally(dropLocation, leadItem);
    }

    public void returnLeadsToSender(DeliveryDrone drone) {
        if (!settings.carryLeashedAnimals() || drone.attachedAnimalTypes().isEmpty()) {
            return;
        }
        int leashCount = drone.attachedAnimalTypes().size();
        giveItemsOrPending(drone.senderId(), List.of(new ItemStack(Material.LEAD, leashCount)));
    }

    public boolean shouldReturnAnimalsToSender(DeliveryDrone drone) {
        return settings.carryLeashedAnimals()
                && !drone.attachedAnimalTypes().isEmpty()
                && !drone.wasOpenedByReceiver();
    }

    public void onDroneReturnedToSender(DeliveryDrone drone) {
        if (!activeDrones.containsKey(drone.droneId())) {
            return;
        }
        respawnAttachedAnimalsAtSender(drone);
        returnLeadsToSender(drone);
        Player sender = Bukkit.getPlayer(drone.senderId());
        if (sender != null && sender.isOnline()) {
            sendMessage(sender, "animal-return-arrived");
        }
        Runnable callback = pendingAnimalReturnCallbacks.remove(drone.droneId());
        if (callback != null) {
            callback.run();
        }
    }

    private void abortDelivery(DeliveryDrone drone, Runnable returnItemsAction) {
        if (!shouldReturnAnimalsToSender(drone)) {
            returnItemsAction.run();
            destroyDroneAfterReturn(drone);
            return;
        }
        pendingAnimalReturnCallbacks.put(drone.droneId(), () -> {
            returnItemsAction.run();
            destroyDroneAfterReturn(drone);
        });
        if (settings.animalReturnMode() == DroneSettings.AnimalReturnMode.TELEPORT) {
            drone.teleportReturnToSender(this);
        } else {
            drone.beginReturnFlight(this);
        }
    }

    public boolean canSenderLaunch(UUID senderId) {
        return activeBySender.getOrDefault(senderId, 0) < maxActiveForSender(senderId);
    }

    public int maxActiveForSender(UUID senderId) {
        Player player = Bukkit.getPlayer(senderId);
        if (player != null) {
            int permissionMax = SendMaxPermissions.resolveMaxFromPermissions(player);
            if (permissionMax > 0) {
                return permissionMax;
            }
        }
        return settings.maxActivePerSender();
    }

    public int maxActivePerSender() {
        return settings.maxActivePerSender();
    }

    public int maxLeashedAnimalsFor(Player player) {
        if (player != null) {
            int permissionMax = SendMaxPermissions.resolveLeashedMaxFromPermissions(player);
            if (permissionMax > 0) {
                return permissionMax;
            }
        }
        return settings.maxLeashedAnimalsPerDrone();
    }

    public int maxSocketsFor(Player player) {
        if (player != null) {
            int permissionMax = SendMaxPermissions.resolveSocketsMaxFromPermissions(player);
            if (permissionMax > 0) {
                return permissionMax;
            }
        }
        return settings.maxSocketsPerPlayer();
    }

    public boolean isBlockedWorld(String worldName) {
        return settings.blockedWorlds().contains(worldName.toLowerCase());
    }

    public DeliveryDrone findByInventory(Inventory inventory) {
        return byInventory.get(inventory);
    }

    public List<DeliveryDrone> activeDronesSnapshot() {
        return new ArrayList<>(activeDrones.values());
    }

    public int activeOutgoingCount(UUID senderId) {
        return activeBySender.getOrDefault(senderId, 0);
    }

    public List<DeliveryDrone> getOutgoingDrones(UUID senderId) {
        return activeDrones.values().stream()
                .filter(drone -> drone.senderId().equals(senderId))
                .toList();
    }

    public List<DeliveryDrone> getIncomingDrones(UUID receiverId) {
        return activeDrones.values().stream()
                .filter(drone -> drone.receiverId().equals(receiverId))
                .toList();
    }

    public int countOutgoing(UUID senderId) {
        return getOutgoingDrones(senderId).size();
    }

    public int countIncoming(UUID receiverId) {
        return getIncomingDrones(receiverId).size();
    }

    public int countIncomingFlying(UUID receiverId) {
        return (int) getIncomingDrones(receiverId).stream().filter(DeliveryDrone::isFlying).count();
    }

    public int countIncomingLanded(UUID receiverId) {
        return (int) getIncomingDrones(receiverId).stream().filter(DeliveryDrone::isLanded).count();
    }

    public DeliveryDrone findNearestLandedDrone(Player player) {
        if (player.getWorld() == null) {
            return null;
        }
        DeliveryDrone nearest = null;
        double nearestSq = Double.MAX_VALUE;
        Location playerLoc = player.getLocation();
        for (DeliveryDrone drone : getIncomingDrones(player.getUniqueId())) {
            if (!drone.isLanded()) {
                continue;
            }
            Location droneLoc = drone.currentLocation();
            if (droneLoc.getWorld() == null || !droneLoc.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distSq = droneLoc.distanceSquared(playerLoc);
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearest = drone;
            }
        }
        return nearest;
    }

    public int pendingReturnStacks(UUID playerId) {
        List<ItemStack> stacks = pendingReturns.get(playerId);
        return stacks == null ? 0 : stacks.size();
    }

    public boolean isDroneFlyingToSocket(String socketName) {
        return activeDrones.values().stream()
            .anyMatch(drone -> drone.socketName() != null && drone.socketName().equals(socketName));
    }

    public void destroyDrone(DeliveryDrone drone, boolean keepForPersistence) {
        if (drone == null) {
            return;
        }
        if (!keepForPersistence) {
            activeDrones.remove(drone.droneId());
            byInventory.remove(drone.inventory());
            // Return items based on despawn mode
            boolean shouldReturnItems = settings.despawnMode() == DroneSettings.DespawnMode.COLLECT || 
                                      (settings.despawnMode() == DroneSettings.DespawnMode.DELETE && !drone.wasOpenedByReceiver());
            if (shouldReturnItems) {
                List<ItemStack> items = drone.snapshotItems();
                if (!items.isEmpty()) {
                    Player sender = Bukkit.getPlayer(drone.senderId());
                    if (sender != null && sender.isOnline()) {
                        // Deliver items immediately to online sender
                        for (ItemStack stack : items) {
                            Map<Integer, ItemStack> overflow = sender.getInventory().addItem(stack);
                            if (!overflow.isEmpty()) {
                                overflow.values().forEach(item -> sender.getWorld().dropItemNaturally(sender.getLocation(), item));
                            }
                        }
                        sendMessage(sender, "return-delivered");
                    } else {
                        // Store for later delivery when player comes online
                        pendingReturns.computeIfAbsent(drone.senderId(), ignored -> new ArrayList<>()).addAll(items);
                    }
                }
            }
            decrementSenderCounter(drone.senderId());
        }
        
        drone.destroy();
        persistence.deleteDrone(drone.droneId());
    }

    public void onDroneStandChanged(DeliveryDrone drone, UUID previousStandId, UUID newStandId) {
        if (previousStandId != null) {
            byEntityUuid.remove(previousStandId);
        }
        if (newStandId != null) {
            byEntityUuid.put(newStandId, drone);
        }
    }

    public void removeDroneFromEntityMap(UUID standId) {
        if (standId != null) {
            byEntityUuid.remove(standId);
        }
    }

    public int declineIncoming(Player receiver) {
        List<DeliveryDrone> incoming = activeDrones.values().stream()
                .filter(drone -> drone.receiverId().equals(receiver.getUniqueId()))
                .toList();
        if (incoming.isEmpty()) {
            return 0;
        }
        for (DeliveryDrone drone : incoming) {
            Player sender = Bukkit.getPlayer(drone.senderId());
            if (sender != null) {
                discordWebhookManager.sendDeliveryDeclined(sender, receiver, drone);
            }
            abortDelivery(drone, () -> returnItemsToSender(drone));
        }
        return incoming.size();
    }

    public int cancelOutgoing(Player sender) {
        List<DeliveryDrone> outgoing = activeDrones.values().stream()
                .filter(drone -> drone.senderId().equals(sender.getUniqueId()))
                .toList();
        if (outgoing.isEmpty()) {
            return 0;
        }
        for (DeliveryDrone drone : outgoing) {
            notifyReceiverOfCancel(sender, drone);
            abortDelivery(drone, () -> returnItemsToSenderOnCancel(drone));
        }
        return outgoing.size();
    }

    public void handlePlayerUnavailable(UUID playerId, boolean dimensionChange) {
        for (DeliveryDrone drone : findDronesAffectedByPlayerUnavailable(playerId)) {
            if (drone.socketName() != null) {
                DeliverySocket socket = findSocketForDrone(drone);
                if (socket == null) {
                    continue;
                }
                if (canReceiveSocketDelivery(socket)) {
                    continue;
                }
                if (drone.wasOpenedByReceiver() || drone.isExpired(currentTick())) {
                    continue;
                }
                cancelAbandonedSocketDrone(drone, socket);
            } else if (drone.receiverId().equals(playerId)) {
                if (dimensionChange) {
                    abortDelivery(drone, () -> returnItemsToSenderDimensionChange(drone));
                } else {
                    abortDelivery(drone, () -> returnItemsToSenderOffline(drone));
                }
            }
        }
    }

    public int receiverWentOffline(UUID receiverId) {
        handlePlayerUnavailable(receiverId, false);
        return 0;
    }

    public int receiverChangedDimension(UUID receiverId) {
        // Cross-dimension delivery allows players to change dimensions without canceling active drones.
        return 0;
    }

    public void deliverPendingReturns(Player player) {
        List<ItemStack> returns = pendingReturns.remove(player.getUniqueId());
        if (returns != null && !returns.isEmpty()) {
            giveItems(player, returns);
            sendMessage(player, "return-delivered");
        }
        deliverSocketPendingReturns(player);
    }

    public void deliverSocketPendingReturns(Player player) {
        List<ItemStack> returns = socketPendingReturns.takeReturns(player.getUniqueId());
        if (returns.isEmpty()) {
            return;
        }
        giveItems(player, returns);
        sendMessage(player, "socket-pending-return-delivered");
    }

    private void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack stack : items) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }
    }

    public void giveItemsToPlayer(Player player, List<ItemStack> items) {
        if (player == null || items == null || items.isEmpty()) {
            return;
        }
        giveItemsOrPending(player.getUniqueId(), items);
    }

    private void giveItemsOrPending(UUID playerId, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            giveItems(player, items);
            return;
        }
        pendingReturns.computeIfAbsent(playerId, ignored -> new ArrayList<>()).addAll(items);
    }

    private void destroyDroneAfterReturn(DeliveryDrone drone) {
        drone.clearItems();
        destroyDrone(drone, false);
    }

    private void notifyReceiverOfCancel(Player sender, DeliveryDrone drone) {
        Player receiver = Bukkit.getPlayer(drone.receiverId());
        if (receiver == null || !receiver.isOnline()) {
            return;
        }
        if (drone.socketName() != null) {
            sendMessage(receiver, "cancel-receiver-notify-socket",
                    "<player>", sender.getName(),
                    "<socket>", drone.socketName());
        } else {
            sendMessage(receiver, "cancel-receiver-notify", "<player>", sender.getName());
        }
    }

    private List<DeliveryDrone> findDronesAffectedByPlayerUnavailable(UUID playerId) {
        List<DeliveryDrone> affected = new ArrayList<>();
        for (DeliveryDrone drone : activeDrones.values()) {
            if (drone.socketName() != null) {
                DeliverySocket socket = findSocketForDrone(drone);
                if (socket != null
                        && (socket.ownerId().equals(playerId) || socket.trustedPlayers().contains(playerId))) {
                    affected.add(drone);
                }
            } else if (drone.receiverId().equals(playerId)) {
                affected.add(drone);
            }
        }
        return affected;
    }

    private DeliverySocket findSocketForDrone(DeliveryDrone drone) {
        if (drone.socketName() == null) {
            return null;
        }
        return socketRepository.getSocket(drone.receiverId(), drone.socketName());
    }

    private boolean canReceiveSocketDelivery(DeliverySocket socket) {
        Player owner = Bukkit.getPlayer(socket.ownerId());
        if (owner != null && owner.isOnline()) {
            return true;
        }
        for (UUID trustedId : socket.trustedPlayers()) {
            Player trusted = Bukkit.getPlayer(trustedId);
            if (trusted != null && trusted.isOnline()) {
                return true;
            }
        }
        return false;
    }

    private void cancelAbandonedSocketDrone(DeliveryDrone drone, DeliverySocket socket) {
        if (!activeDrones.containsKey(drone.droneId())) {
            return;
        }
        Player sender = Bukkit.getPlayer(drone.senderId());
        if (sender != null && sender.isOnline()) {
            sendMessage(sender, "socket-abandoned-sender-notify",
                    "<socket>", socket.name(),
                    "<owner>", socket.ownerName());
        }
        if (shouldReturnAnimalsToSender(drone)) {
            abortDelivery(drone, () -> returnItemsForAbandonedSocket(drone, socket));
            return;
        }
        List<ItemStack> items = drone.snapshotItems();
        drone.clearItems();
        destroyDrone(drone, false);
        if (!items.isEmpty()) {
            socketPendingReturns.addReturns(socket.ownerId(), items);
        }
    }

    private void returnItemsForAbandonedSocket(DeliveryDrone drone, DeliverySocket socket) {
        List<ItemStack> items = drone.snapshotItems();
        drone.clearItems();
        if (items.isEmpty()) {
            return;
        }
        Player sender = Bukkit.getPlayer(drone.senderId());
        if (sender != null && sender.isOnline()) {
            giveItems(sender, items);
            return;
        }
        socketPendingReturns.addReturns(socket.ownerId(), items);
    }

    private void returnItemsToSender(DeliveryDrone drone) {
        List<ItemStack> items = drone.snapshotItems();
        if (items.isEmpty()) {
            return;
        }
        Player sender = Bukkit.getPlayer(drone.senderId());
        if (sender == null || !sender.isOnline()) {
            pendingReturns.computeIfAbsent(drone.senderId(), ignored -> new ArrayList<>()).addAll(items);
            return;
        }
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = sender.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(stack -> sender.getWorld().dropItemNaturally(sender.getLocation(), stack));
            }
        }
        sendMessage(sender, "decline-sender-notify", "<player>", drone.receiverName());
        drone.clearItems();
    }

    private void returnItemsToSenderOnCancel(DeliveryDrone drone) {
        List<ItemStack> items = drone.snapshotItems();
        if (items.isEmpty()) {
            return;
        }
        Player sender = Bukkit.getPlayer(drone.senderId());
        if (sender == null || !sender.isOnline()) {
            pendingReturns.computeIfAbsent(drone.senderId(), ignored -> new ArrayList<>()).addAll(items);
            return;
        }
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = sender.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(stack -> sender.getWorld().dropItemNaturally(sender.getLocation(), stack));
            }
        }
    }

    private void returnItemsToSenderOffline(DeliveryDrone drone) {
        List<ItemStack> items = drone.snapshotItems();
        if (items.isEmpty()) {
            return;
        }
        Player sender = Bukkit.getPlayer(drone.senderId());
        if (sender == null || !sender.isOnline()) {
            pendingReturns.computeIfAbsent(drone.senderId(), ignored -> new ArrayList<>()).addAll(items);
            return;
        }
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = sender.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(stack -> sender.getWorld().dropItemNaturally(sender.getLocation(), stack));
            }
        }
        sendMessage(sender, "receiver-offline-return", "<player>", drone.receiverName());
        drone.clearItems();
    }

    private void returnItemsToSenderDimensionChange(DeliveryDrone drone) {
        List<ItemStack> items = drone.snapshotItems();
        if (items.isEmpty()) {
            return;
        }
        Player sender = Bukkit.getPlayer(drone.senderId());
        if (sender == null || !sender.isOnline()) {
            pendingReturns.computeIfAbsent(drone.senderId(), ignored -> new ArrayList<>()).addAll(items);
            return;
        }
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = sender.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(stack -> sender.getWorld().dropItemNaturally(sender.getLocation(), stack));
            }
        }
        sendMessage(sender, "receiver-dimension-return", "<player>", drone.receiverName());
    }

    public Component renderHologram(DeliveryDrone drone, long currentTick) {
        long remaining = Math.max(0L, settings.despawnTicks() - (currentTick - drone.lastInteractionTick()));
        long totalSeconds = remaining / 20L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        
        // Use different format based on delivery target
        String format;
        TagResolver resolver;
        
        if (drone.socketName() != null) {
            // Socket delivery: use socket format with socket name placeholder
            format = settings.hologramFormatSocket();
            resolver = TagResolver.resolver(
                    Placeholder.unparsed("socket", drone.socketName()),
                    Placeholder.unparsed("minutes", String.valueOf(minutes)),
                    Placeholder.unparsed("seconds", String.format("%02d", seconds))
            );
        } else {
            // Player delivery: use receiver format with receiver name placeholder
            format = settings.hologramFormat();
            resolver = TagResolver.resolver(
                    Placeholder.unparsed("receiver", drone.receiverName()),
                    Placeholder.unparsed("minutes", String.valueOf(minutes)),
                    Placeholder.unparsed("seconds", String.format("%02d", seconds))
            );
        }
        
        return miniMessage.deserialize(format, resolver);
    }

    public String renderBossbar(double distance, long etaSeconds) {
        String rounded = String.valueOf((int) Math.round(distance));
        String format = settings.bossbarFormat();
        format = format.replace("<distance>", rounded);
        format = format.replace("<eta>", String.valueOf(Math.max(0L, etaSeconds)));
        return format;
    }

    public String renderBossbarSocket(String socketName, long etaSeconds) {
        String format = settings.bossbarFormatSocket();
        format = format.replace("<socket>", socketName);
        format = format.replace("<eta>", String.valueOf(Math.max(0L, etaSeconds)));
        return format;
    }

    private void cleanupExpired() {
        long tick = currentTick();
        List<DeliveryDrone> toRemove = null;
        for (DeliveryDrone drone : activeDrones.values()) {
            if (drone.isExpired(tick)) {
                if (toRemove == null) {
                    toRemove = new java.util.ArrayList<>();
                }
                toRemove.add(drone);
            }
        }
        if (toRemove == null) {
            return;
        }
        for (DeliveryDrone drone : toRemove) {
            Player sender = Bukkit.getPlayer(drone.senderId());
            Player receiver = Bukkit.getPlayer(drone.receiverId());
            if (sender != null && receiver != null) {
                discordWebhookManager.sendDeliveryExpired(sender, receiver, drone);
            }
            abortDelivery(drone, () -> returnExpiredItems(drone));
        }
    }

    private void returnExpiredItems(DeliveryDrone drone) {
        boolean shouldReturnItems = settings.despawnMode() == DroneSettings.DespawnMode.COLLECT
                || (settings.despawnMode() == DroneSettings.DespawnMode.DELETE && !drone.wasOpenedByReceiver());
        if (!shouldReturnItems) {
            return;
        }
        List<ItemStack> items = drone.snapshotItems();
        if (items.isEmpty()) {
            return;
        }
        giveItemsOrPending(drone.senderId(), items);
        drone.clearItems();
    }

    private long currentTick() {
        return Bukkit.getCurrentTick();
    }

    private void incrementSenderCounter(UUID senderId) {
        activeBySender.merge(senderId, 1, Integer::sum);
    }

    private void decrementSenderCounter(UUID senderId) {
        int current = activeBySender.getOrDefault(senderId, 0);
        if (current <= 1) {
            activeBySender.remove(senderId);
            return;
        }
        activeBySender.put(senderId, current - 1);
    }

    private String getPlayerName(UUID playerId) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        
        // Try to get from offline player
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        if (offlinePlayer != null && offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }
        
        return "Unbekannt";
    }

    private void loadChunkNow(Location location) {
        // Use the performance optimizer's async chunk loading instead of blocking the main thread
        performanceOptimizer.loadChunkOptimized(location);
    }

    private void cleanupAllOldDrones() {
        plugin.getLogger().info("Cleaning up old drone ArmorStands from previous sessions...");
        java.util.concurrent.atomic.AtomicInteger removedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            world.getEntities().stream()
                .filter(entity -> entity instanceof org.bukkit.entity.ArmorStand)
                .forEach(armorStand -> {
                    org.bukkit.entity.ArmorStand stand = (org.bukkit.entity.ArmorStand) armorStand;
                    boolean isOldDrone = false;
                    
                    // Check if it's not registered in our system (likely old drone)
                    if (!byEntityUuid.containsKey(stand.getUniqueId())) {
                        // Check for drone characteristics
                        if (stand.isInvisible()) {
                            isOldDrone = true;
                        }
                        
                        if (!isOldDrone && stand.isCustomNameVisible() && stand.getCustomName() != null) {
                            String name = stand.getCustomName();
                            if (name.contains("Drone") || name.contains("Drohne")) {
                                isOldDrone = true;
                            }
                        }
                        
                        // Check for typical drone properties
                        if (!isOldDrone && stand.hasGravity() == false) {
                            isOldDrone = true;
                        }
                    }
                    
                    if (isOldDrone) {
                        plugin.getLogger().info("Removing old drone ArmorStand: " + stand.getUniqueId() + 
                            " at " + stand.getLocation());
                        stand.remove();
                        removedCount.incrementAndGet();
                    }
                });
        }
        
        int count = removedCount.get();
        if (count > 0) {
            plugin.getLogger().info("Removed " + count + " old drone ArmorStands");
        } else {
            plugin.getLogger().info("No old drone ArmorStands found");
        }
    }

    private void handleRestartSafeCleanup() {
        plugin.getLogger().info("Performing restart-safe cleanup for " + activeDrones.size() + " active drones...");
        
        // Synchronously process all drones to ensure completion before shutdown
        for (DeliveryDrone drone : new ArrayList<>(activeDrones.values())) {
            try {
                // Kill all drone Armor Stands immediately, as they will be respawned on load
                killDroneArmorStands(drone);
                
                // Animals that were spawned because of landing need to be removed as they'll be respawned
                for (UUID animalId : drone.getSpawnedTransportAnimalIds()) {
                    Entity entity = Bukkit.getEntity(animalId);
                    if (entity != null) {
                        entity.remove();
                    }
                }
                
                // Clean up drone data
                cleanupDroneData(drone);
            } catch (Exception e) {
                plugin.getLogger().warning("Error during restart-safe cleanup for drone " + drone.droneId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // Force cleanup of any remaining ArmorStands
        cleanupAllRemainingDrones();
        
        plugin.getLogger().info("Restart-safe cleanup completed. All drones processed.");
    }
    
    private void returnItemsToSenderRestart(DeliveryDrone drone) {
        List<ItemStack> items = drone.snapshotItems();
        if (items.isEmpty()) {
            return;
        }
        
        Player sender = Bukkit.getPlayer(drone.senderId());
        if (sender == null || !sender.isOnline()) {
            // Store for later delivery when player comes online
            pendingReturns.computeIfAbsent(drone.senderId(), ignored -> new ArrayList<>()).addAll(items);
            return;
        }
        
        // Return items directly to sender's inventory
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = sender.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(stack -> sender.getWorld().dropItemNaturally(sender.getLocation(), stack));
            }
        }
        
        sendMessage(sender, "restart-return-items", "<player>", drone.receiverName());
    }
    
    public void respawnAttachedAnimalsAtSender(DeliveryDrone drone) {
        returnAnimalsToOriginalLocation(drone);
    }

    private void returnAnimalsToOriginalLocation(DeliveryDrone drone) {
        Location originalLocation = drone.originalSendLocation();
        if (originalLocation.getWorld() == null) {
            return;
        }

        World world = originalLocation.getWorld();
        if (!world.isChunkLoaded(originalLocation.getBlockX() >> 4, originalLocation.getBlockZ() >> 4)) {
            world.getChunkAt(originalLocation.getBlockX() >> 4, originalLocation.getBlockZ() >> 4).load();
        }

        Location safeLocation = findSafeSpawnLocation(originalLocation);

        for (UUID animalId : new ArrayList<>(drone.getSpawnedTransportAnimalIds())) {
            Entity entity = Bukkit.getEntity(animalId);
            if (entity instanceof LivingEntity animal && !animal.isDead()) {
                // Store animal properties before removal
                double health = animal.getHealth();
                double maxHealth = animal.getMaxHealth();
                boolean isBaby = false;
                boolean isTamed = false;
                org.bukkit.entity.AnimalTamer owner = null;
                
                // Check if it's a baby animal
                if (animal instanceof org.bukkit.entity.Ageable ageable) {
                    isBaby = !ageable.isAdult();
                }
                
                // Check if it's tamed and store owner
                if (animal instanceof org.bukkit.entity.Tameable tameable) {
                    isTamed = tameable.isTamed();
                    owner = tameable.getOwner();
                }
                
                // Remove the current animal
                animal.remove();
                
                // Respawn safely at original location
                try {
                    Entity newAnimal = safeLocation.getWorld().spawnEntity(safeLocation.clone().add(0.0, 1.0, 0.0), animal.getType());
                    if (newAnimal instanceof LivingEntity newLiving) {
                        // Restore health with safety check
                        double safeHealth = Math.min(health, maxHealth);
                        newLiving.setHealth(safeHealth);
                        
                        // Apply temporary fall damage immunity
                        applyFallProtection(newLiving);
                        
                        // Restore age if it was a baby
                        if (isBaby && newLiving instanceof org.bukkit.entity.Ageable newAgeable) {
                            newAgeable.setBaby();
                        }
                        
                        // Restore taming status
                        if (isTamed && newLiving instanceof org.bukkit.entity.Tameable newTameable && owner != null) {
                            newTameable.setTamed(true);
                            newTameable.setOwner(owner);
                        }
                        
                        plugin.getLogger().info("Safely respawned " + animal.getType() + " at original location with health: " + safeHealth);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to safely respawn animal at original location: " + e.getMessage());
                }
            }
        }

        List<UUID> spawnedIds = drone.getSpawnedTransportAnimalIds();
        if (spawnedIds.isEmpty() && !drone.attachedAnimalTypes().isEmpty()) {
            int index = 0;
            for (EntityType type : drone.attachedAnimalTypes()) {
                if (!type.isAlive() || type == EntityType.PLAYER || type == EntityType.ARMOR_STAND) {
                    continue;
                }
                Location spawnAt = safeLocation.clone().add((index % 3) * 0.6, 0.0, (index / 3) * 0.6);
                try {
                    Entity entity = world.spawnEntity(spawnAt, type);
                    if (entity instanceof LivingEntity living) {
                        applyFallProtection(living);
                    } else {
                        entity.remove();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to respawn transported animal " + type + ": " + e.getMessage());
                }
                index++;
            }
        }
    }

    public Location resolveSafeReturnLanding(Location reference) {
        World world = reference.getWorld();
        if (world == null) {
            return reference.clone();
        }
        int x = reference.getBlockX();
        int z = reference.getBlockZ();
        int refY = reference.getBlockY();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.getChunkAt(x >> 4, z >> 4).load();
        }
        int safeY = findSafeLandingY(world, x, refY, z);
        return new Location(world, x + 0.5, safeY + 0.1, z + 0.5, reference.getYaw(), reference.getPitch());
    }

    private Location findSafeSpawnLocation(Location originalLocation) {
        return resolveSafeReturnLanding(originalLocation);
    }

    private static boolean isSafeGround(World world, int x, int y, int z) {
        org.bukkit.block.Block ground = world.getBlockAt(x, y, z);
        org.bukkit.block.Block feet = world.getBlockAt(x, y + 1, z);
        org.bukkit.block.Block head = world.getBlockAt(x, y + 2, z);
        return ground.getType().isSolid()
                && !ground.isLiquid()
                && ground.getType() != Material.BEDROCK
                && !feet.getType().isSolid()
                && !feet.isLiquid()
                && !head.getType().isSolid()
                && !head.isLiquid();
    }

    private static int findSafeLandingY(World world, int x, int startY, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return Math.max(world.getMinHeight() + 1, startY);
        }
        int highest = world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (highest >= world.getMinHeight() && highest < world.getMaxHeight() - 2 && isSafeGround(world, x, highest, z)) {
            return highest + 1;
        }
        for (int dy = 0; dy <= 16; dy++) {
            int yDown = startY - dy;
            if (yDown >= world.getMinHeight() && yDown < world.getMaxHeight() - 2 && isSafeGround(world, x, yDown, z)) {
                return yDown + 1;
            }
            if (dy > 0) {
                int yUp = startY + dy;
                if (yUp >= world.getMinHeight() && yUp < world.getMaxHeight() - 2 && isSafeGround(world, x, yUp, z)) {
                    return yUp + 1;
                }
            }
        }
        if (world.getEnvironment() == World.Environment.NETHER && highest >= 120) {
            for (int y = 115; y > world.getMinHeight(); y--) {
                if (isSafeGround(world, x, y, z)) {
                    return y + 1;
                }
            }
        }
        return Math.max(world.getMinHeight() + 1, highest + 1);
    }
    
    private void applyFallProtection(LivingEntity entity) {
        // Apply temporary invulnerability to prevent fall damage
        entity.setInvulnerable(true);
        
        // Schedule removal of invulnerability after a safe period
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entity != null && !entity.isDead()) {
                entity.setInvulnerable(false);
            }
        }, 60L); // 3 seconds of protection (60 ticks)
    }
    
    private void killDroneArmorStands(DeliveryDrone drone) {
        try {
            // Kill the main drone ArmorStand
            UUID standId = drone.standId();
            if (standId != null) {
                Entity entity = Bukkit.getEntity(standId);
                if (entity instanceof ArmorStand armorStand && !armorStand.isDead()) {
                    armorStand.remove();
                    plugin.getLogger().info("Killed drone ArmorStand: " + standId);
                }
            }
            
            // Kill any hologram ArmorStands
            Location droneLoc = drone.currentLocation();
            if (droneLoc != null && droneLoc.getWorld() != null) {
                for (Entity entity : droneLoc.getWorld().getNearbyEntities(droneLoc, 5.0, 5.0, 5.0)) {
                    if (entity instanceof ArmorStand hologram && !hologram.isDead()) {
                        if (hologram.isCustomNameVisible() && hologram.getCustomName() != null && hologram.getCustomName().contains("Drone")) {
                            hologram.remove();
                            plugin.getLogger().info("Killed hologram ArmorStand near drone " + drone.droneId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error killing drone ArmorStands: " + e.getMessage());
        }
    }
    
    private void cleanupDroneData(DeliveryDrone drone) {
        try {
            // Remove from all tracking maps
            activeDrones.remove(drone.droneId());
            if (drone.standId() != null) {
                byEntityUuid.remove(drone.standId());
            }
            byInventory.remove(drone.inventory());
            
            // Decrement sender counter
            decrementSenderCounter(drone.senderId());
            
            // Clear drone's internal data
            drone.clearItems();
            
            plugin.getLogger().info("Cleaned up data for drone " + drone.droneId());
        } catch (Exception e) {
            plugin.getLogger().warning("Error cleaning up drone data: " + e.getMessage());
        }
    }
    
    private void cleanupAllRemainingDrones() {
        plugin.getLogger().info("Performing final cleanup of any remaining drone entities...");
        
        // Find and remove any remaining drone-related ArmorStands
        for (World world : Bukkit.getWorlds()) {
            world.getEntities().stream()
                .filter(entity -> entity instanceof ArmorStand)
                .forEach(armorStand -> {
                    ArmorStand stand = (ArmorStand) armorStand;
                    boolean isDrone = false;
                    
                    // Check for drone characteristics
                    if (stand.isInvisible() || 
                        (stand.getCustomName() != null && 
                         (stand.getCustomName().contains("Drone") || stand.getCustomName().contains("Drohne"))) ||
                        (!stand.hasGravity() && stand.isInvulnerable())) {
                        isDrone = true;
                    }
                    
                    if (isDrone && !stand.isDead()) {
                        stand.remove();
                        plugin.getLogger().info("Removed remaining drone ArmorStand: " + stand.getUniqueId());
                    }
                });
        }
    }

    private void cleanupOldArmorStands(Location location) {
        if (location.getWorld() == null) return;
        
        // Find all ArmorStands within 5 blocks of the restore location (more aggressive)
        location.getWorld().getNearbyEntities(location, 5.0, 5.0, 5.0).stream()
            .filter(entity -> entity instanceof org.bukkit.entity.ArmorStand)
            .forEach(armorStand -> {
                // Check if this ArmorStand looks like a drone (has custom name or specific properties)
                org.bukkit.entity.ArmorStand stand = (org.bukkit.entity.ArmorStand) armorStand;
                boolean isDrone = false;
                
                // Check for drone characteristics
                if (stand.isCustomNameVisible() && stand.getCustomName() != null) {
                    String name = stand.getCustomName();
                    if (name.contains("Drone") || name.contains("Drohne")) {
                        isDrone = true;
                    }
                }
                
                // Check for invisible marker (drones are usually invisible)
                if (!isDrone && stand.isInvisible()) {
                    isDrone = true;
                }
                
                // Check if it's not registered in our system (likely old drone)
                if (!byEntityUuid.containsKey(stand.getUniqueId())) {
                    isDrone = true;
                }
                
                if (isDrone) {
                    plugin.getLogger().info("Removing old drone ArmorStand: " + stand.getUniqueId() + 
                        " at " + stand.getLocation());
                    stand.remove();
                }
            });
    }
}
