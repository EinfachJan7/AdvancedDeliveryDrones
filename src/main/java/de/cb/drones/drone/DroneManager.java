package de.cb.drones.drone;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import java.util.ArrayList;
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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class DroneManager {
    private final AdvancedDeliveryDronesPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, DeliveryDrone> activeDrones = new HashMap<>();
    private final Map<UUID, DeliveryDrone> byEntityUuid = new HashMap<>();
    private final Map<Inventory, DeliveryDrone> byInventory = new HashMap<>();
    private final Map<UUID, Integer> activeBySender = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>();
    private DroneSettings settings;
    private BukkitTask cleanupTask;

    public DroneManager(AdvancedDeliveryDronesPlugin plugin, DroneSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
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
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpired, 20L, 20L);
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        for (DeliveryDrone drone : new ArrayList<>(activeDrones.values())) {
            destroyDrone(drone, false);
        }
        activeDrones.clear();
        byEntityUuid.clear();
    }

    public DeliveryDrone spawnDrone(Player sender, Player receiver, Inventory inventory, List<LivingEntity> attachedAnimals) {
        return spawnDrone(sender, receiver, inventory, receiver.getLocation().clone(), attachedAnimals);
    }

    public DeliveryDrone spawnDrone(Player sender, Player receiver, Inventory inventory, Location fixedTarget, List<LivingEntity> attachedAnimals) {
        Location start = sender.getLocation().clone().add(0, 2.2, 0);
        ArmorStand stand = DeliveryDrone.spawnDroneEntity(start, settings.skullTexture());
        if (stand == null) {
            return null;
        }
        List<UUID> attachedAnimalIds = attachedAnimals == null
                ? List.of()
                : attachedAnimals.stream().map(LivingEntity::getUniqueId).toList();
        UUID id = UUID.randomUUID();
        DeliveryDrone drone = new DeliveryDrone(
                id,
                sender.getUniqueId(),
                receiver.getUniqueId(),
                receiver.getName(),
                fixedTarget,
                inventory,
                attachedAnimalIds,
                settings,
                stand,
                currentTick()
        );
        activeDrones.put(id, drone);
        byEntityUuid.put(drone.standId(), drone);
        byInventory.put(inventory, drone);
        incrementSenderCounter(sender.getUniqueId());
        drone.attachLeashedAnimal();
        drone.startFlight(this);
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
        }
        drone.markInteraction(currentTick());
        player.openInventory(drone.inventory());
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

    public void destroyDrone(DeliveryDrone drone, boolean dueToDespawn) {
        activeDrones.remove(drone.droneId());
        byEntityUuid.remove(drone.standId());
        byInventory.remove(drone.inventory());
        if (!drone.wasOpenedByReceiver()) {
            decrementSenderCounter(drone.senderId());
        }
        drone.clearItems();
        drone.destroy();
    }

    public int declineIncoming(Player receiver) {
        List<DeliveryDrone> incoming = activeDrones.values().stream()
                .filter(drone -> drone.receiverId().equals(receiver.getUniqueId()))
                .toList();
        if (incoming.isEmpty()) {
            return 0;
        }
        for (DeliveryDrone drone : incoming) {
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
}
