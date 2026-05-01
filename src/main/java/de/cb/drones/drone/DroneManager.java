package de.cb.drones.drone;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.discord.DiscordWebhookManager;
import de.cb.drones.performance.PerformanceOptimizer;
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
    private final Map<UUID, DeliveryDrone> activeDrones = new HashMap<>();
    private final Map<UUID, DeliveryDrone> byEntityUuid = new HashMap<>();
    private final Map<Inventory, DeliveryDrone> byInventory = new HashMap<>();
    private final Map<UUID, Integer> activeBySender = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>();
    private DroneSettings settings;
    private BukkitTask cleanupTask;

    public DroneManager(AdvancedDeliveryDronesPlugin plugin, DroneSettings settings, DiscordWebhookManager discordWebhookManager) {
        this.plugin = plugin;
        this.settings = settings;
        this.discordWebhookManager = discordWebhookManager;
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
        
        for (DeliveryDrone drone : new ArrayList<>(activeDrones.values())) {
            destroyDrone(drone, false);
        }
        activeDrones.clear();
        byEntityUuid.clear();
    }

    public DeliveryDrone spawnDrone(Player sender, Player receiver, Inventory inventory, List<LivingEntity> attachedAnimals, boolean animalsOnlyDelivery) {
        return spawnDrone(sender, receiver, inventory, receiver.getLocation().clone(), attachedAnimals, animalsOnlyDelivery, false);
    }

    public DeliveryDrone spawnDrone(
            Player sender,
            Player receiver,
            Inventory inventory,
            Location fixedTarget,
            List<LivingEntity> attachedAnimals,
            boolean animalsOnlyDelivery,
            boolean forceTargetChunkLoad
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
        }
        drone.markInteraction(currentTick());
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
        }
        drone.releaseLeashedAnimal();
        drone.markInteraction(currentTick());
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
            if (!drone.wasOpenedByReceiver()) {
                pendingReturns.put(drone.senderId(), drone.snapshotItems());
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
        sender.sendMessage(message("return-delivered", null, null));
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
        sender.sendMessage(message("decline-sender-notify", "<player>", drone.receiverName()));
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
        sender.sendMessage(message("receiver-offline-return", "<player>", drone.receiverName()));
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
        sender.sendMessage(message("receiver-dimension-return", "<player>", drone.receiverName()));
    }

    public Component renderHologram(DeliveryDrone drone, long currentTick) {
        long remaining = Math.max(0L, settings.despawnTicks() - (currentTick - drone.lastInteractionTick()));
        long totalSeconds = remaining / 20L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        TagResolver resolver = TagResolver.resolver(
                Placeholder.unparsed("receiver", drone.receiverName()),
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
            
            destroyDrone(drone, true);
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
