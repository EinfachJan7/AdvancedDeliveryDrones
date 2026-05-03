package de.cb.drones.drone;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.discord.DiscordWebhookManager;
import de.cb.drones.performance.PerformanceOptimizer;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class DroneManager {
    private final AdvancedDeliveryDronesPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final DiscordWebhookManager discordWebhookManager;
    private final PerformanceOptimizer performanceOptimizer;
    private final SocketRepository socketRepository;
    private final Map<UUID, DeliveryDrone> activeDrones = new HashMap<>();
    private final Map<UUID, DeliveryDrone> byEntityUuid = new HashMap<>();
    private final Map<Inventory, DeliveryDrone> byInventory = new HashMap<>();
    private final Map<UUID, Integer> activeBySender = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>();
    private DroneSettings settings;
    private BukkitTask cleanupTask;

    public DroneManager(AdvancedDeliveryDronesPlugin plugin, DroneSettings settings, DiscordWebhookManager discordWebhookManager, SocketRepository socketRepository) {
        this.plugin = plugin;
        this.settings = settings;
        this.discordWebhookManager = discordWebhookManager;
        this.socketRepository = socketRepository;
        this.performanceOptimizer = new PerformanceOptimizer(plugin);
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
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String body = plugin.getConfig().getString("messages." + key, key);
        body = body.replace(placeholder1, value1).replace(placeholder2, value2);
        player.sendMessage(miniMessage.deserialize(prefix + body));
    }

    public void sendMessage(Player player, String key, String placeholder1, String value1, String placeholder2, String value2, String placeholder3, String value3) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String body = plugin.getConfig().getString("messages." + key, key);
        body = body.replace(placeholder1, value1).replace(placeholder2, value2).replace(placeholder3, value3);
        player.sendMessage(miniMessage.deserialize(prefix + body));
    }

    public void start() {
        // Clean up any old drone ArmorStands from previous sessions
        cleanupAllOldDrones();
        
        // Load saved drones asynchronously
        // loadSavedDronesAsync();
        
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpired, 20L, 20L);
    }

    public PerformanceOptimizer getPerformanceOptimizer() {
        return performanceOptimizer;
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        performanceOptimizer.shutdown();
        
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
        ArmorStand stand = DeliveryDrone.spawnDroneEntity(start, settings.skullTexture());
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

    public boolean canSenderLaunch(UUID senderId) {
        return activeBySender.getOrDefault(senderId, 0) < settings.maxActivePerSender();
    }

    public int maxActivePerSender() {
        return settings.maxActivePerSender();
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
            UUID standId = drone.standId();
            if (standId != null) {
                byEntityUuid.remove(standId);
            }
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
    }

    public void onDroneStandChanged(DeliveryDrone drone, UUID previousStandId, UUID newStandId) {
        if (previousStandId != null) {
            byEntityUuid.remove(previousStandId);
        }
        if (newStandId != null) {
            byEntityUuid.put(newStandId, drone);
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
            // Send Discord notification for declined delivery
            Player sender = Bukkit.getPlayer(drone.senderId());
            if (sender != null) {
                discordWebhookManager.sendDeliveryDeclined(sender, receiver, drone);
            }
            
            returnItemsToSender(drone);
            destroyDrone(drone, false);
        }
        return incoming.size();
    }

    public int receiverWentOffline(UUID receiverId) {
        List<DeliveryDrone> incoming = activeDrones.values().stream()
                .filter(drone -> drone.receiverId().equals(receiverId))
                .toList();
        if (incoming.isEmpty()) {
            return 0;
        }
        for (DeliveryDrone drone : incoming) {
            returnItemsToSenderOffline(drone);
            destroyDrone(drone, false);
        }
        return incoming.size();
    }

    public int receiverChangedDimension(UUID receiverId) {
        List<DeliveryDrone> incoming = activeDrones.values().stream()
                .filter(drone -> drone.receiverId().equals(receiverId))
                .toList();
        if (incoming.isEmpty()) {
            return 0;
        }
        for (DeliveryDrone drone : incoming) {
            returnItemsToSenderDimensionChange(drone);
            destroyDrone(drone, false);
        }
        return incoming.size();
    }

    public void deliverPendingReturns(Player sender) {
        List<ItemStack> returns = pendingReturns.remove(sender.getUniqueId());
        if (returns == null || returns.isEmpty()) {
            return;
        }
        for (ItemStack stack : returns) {
            Map<Integer, ItemStack> overflow = sender.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(item -> sender.getWorld().dropItemNaturally(sender.getLocation(), item));
            }
        }
        sendMessage(sender, "return-delivered");
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
        
        // Use socket name if drone is sent to a socket, otherwise use receiver name
        String displayTarget = drone.socketName() != null ? drone.socketName() : drone.receiverName();
        
        TagResolver resolver = TagResolver.resolver(
                Placeholder.unparsed("receiver", displayTarget),
                Placeholder.unparsed("minutes", String.valueOf(minutes)),
                Placeholder.unparsed("seconds", String.format("%02d", seconds))
        );
        return miniMessage.deserialize(settings.hologramFormat(), resolver);
    }

    public String renderBossbar(double distance, long etaSeconds) {
        String rounded = String.valueOf((int) Math.round(distance));
        Component component = miniMessage.deserialize(
                settings.bossbarFormat(),
                Placeholder.unparsed("distance", rounded),
                Placeholder.unparsed("eta", String.valueOf(Math.max(0L, etaSeconds)))
        );
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private void cleanupExpired() {
        long tick = currentTick();
        List<DeliveryDrone> toRemove = activeDrones.values().stream()
                .filter(drone -> drone.isExpired(tick))
                .toList();
        for (DeliveryDrone drone : toRemove) {
            // Send Discord notification for expired drone
            Player sender = Bukkit.getPlayer(drone.senderId());
            Player receiver = Bukkit.getPlayer(drone.receiverId());
            if (sender != null && receiver != null) {
                discordWebhookManager.sendDeliveryExpired(sender, receiver, drone);
            }
            
            destroyDrone(drone, false);
        }
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
                plugin.getLogger().info("Processing drone " + drone.droneId() + " for restart cleanup...");
                
                // Step 1: Kill all drone Armor Stands immediately
                killDroneArmorStands(drone);
                
                // Step 2: Return items to sender (synchronous)
                returnItemsToSenderRestart(drone);
                
                // Step 3: Return transported animals to their original locations (synchronous)
                returnAnimalsToOriginalLocation(drone);
                
                // Step 4: Send Discord notification for restart cleanup
                Player sender = Bukkit.getPlayer(drone.senderId());
                Player receiver = Bukkit.getPlayer(drone.receiverId());
                if (sender != null && receiver != null) {
                    discordWebhookManager.sendDeliveryDeclined(sender, receiver, drone);
                }
                
                // Step 5: Clean up drone data
                cleanupDroneData(drone);
                
                plugin.getLogger().info("Restart-safe cleanup completed for drone " + drone.droneId());
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
    
    private void returnAnimalsToOriginalLocation(DeliveryDrone drone) {
        // Get the start location from the drone
        Location originalLocation = drone.startLocation();
        if (originalLocation == null || originalLocation.getWorld() == null) {
            return;
        }
        
        // Ensure chunk is loaded at original location
        if (!originalLocation.getWorld().isChunkLoaded(originalLocation.getBlockX() >> 4, originalLocation.getBlockZ() >> 4)) {
            originalLocation.getWorld().getChunkAt(originalLocation.getBlockX() >> 4, originalLocation.getBlockZ() >> 4).load();
        }
        
        // Find safe spawn location with fall protection
        Location safeLocation = findSafeSpawnLocation(originalLocation);
        
        // Remove spawned animals and respawn them safely at original location
        for (UUID animalId : drone.getSpawnedTransportAnimalIds()) {
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
        
        plugin.getLogger().info("Safely returned " + drone.getSpawnedTransportAnimalIds().size() + " animals to original location");
    }
    
    private Location findSafeSpawnLocation(Location originalLocation) {
        World world = originalLocation.getWorld();
        int x = originalLocation.getBlockX();
        int z = originalLocation.getBlockZ();
        
        // Find the highest safe ground level
        int y = world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        
        // Ensure we're not spawning too high (prevent fall damage)
        int maxY = y + 3; // Maximum 3 blocks above ground
        int spawnY = Math.min(originalLocation.getBlockY(), maxY);
        
        // Ensure minimum height (not below bedrock)
        spawnY = Math.max(spawnY, world.getMinHeight() + 1);
        
        return new Location(world, x + 0.5, spawnY, z + 0.5);
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
            for (Entity entity : Bukkit.selectEntities(null, "@e[type=armor_stand,custom_name=Drone]")) {
                if (entity instanceof ArmorStand hologram && !hologram.isDead()) {
                    // Check if this hologram belongs to our drone by proximity
                    Location droneLoc = drone.currentLocation();
                    if (droneLoc != null && hologram.getLocation().distanceSquared(droneLoc) <= 25.0) {
                        hologram.remove();
                        plugin.getLogger().info("Killed hologram ArmorStand for drone " + drone.droneId());
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
