package de.cb.drones.drone;

import de.cb.drones.socket.DeliverySocket;
import de.cb.drones.socket.SocketRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class DroneInteractionListener implements Listener {
    private final DroneManager droneManager;
    private final SocketRepository socketRepository;

    public DroneInteractionListener(DroneManager droneManager, SocketRepository socketRepository) {
        this.droneManager = droneManager;
        this.socketRepository = socketRepository;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractAtEntityEvent event) {
        boolean handled = handleInteract(event.getPlayer(), event.getRightClicked());
        if (handled) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        boolean handled = handleInteract(event.getPlayer(), event.getRightClicked());
        if (handled) {
            event.setCancelled(true);
        }
    }

    private boolean handleInteract(Player player, Entity clicked) {
        // Ignore block displays (socket previews)
        if (clicked instanceof org.bukkit.entity.BlockDisplay) {
            return false;
        }
        
        DeliveryDrone drone = droneManager.findByEntity(clicked.getUniqueId());
        if (drone == null) {
            return false;
        }
        
        // Check if player is authorized to open the drone
        boolean isAuthorized = drone.receiverId().equals(player.getUniqueId());
        boolean isSocketPickup = false;
        DeliverySocket socket = null;
        
        // If not the receiver, check if this is a socket delivery and player is trusted
        if (!isAuthorized && drone.socketName() != null) {
            // Find the socket by searching all sockets for the matching name
            socket = socketRepository.getAllSockets().stream()
                    .filter(s -> s.name().equals(drone.socketName()))
                    .findFirst()
                    .orElse(null);
            if (socket != null && (socket.ownerId().equals(player.getUniqueId()) || socket.trustedPlayers().contains(player.getUniqueId()))) {
                isAuthorized = true;
                isSocketPickup = true;
            }
        }
        
        if (!isAuthorized) {
            droneManager.sendMessage(player, "wrong-user");
            return true;
        }

        if (drone.isFlying()) {
            droneManager.sendMessage(player, "drone-flying");
            return true;
        }

        if (drone.isAnimating()) {
            droneManager.sendMessage(player, "drone-flying");
            return true;
        }
        if (droneManager.settings().carryLeashedAnimals() && drone.animalsOnlyDelivery()) {
            droneManager.handleAnimalOnlyInteract(drone);
            droneManager.destroyDrone(drone, false);
            return true;
        }

        // Track socket pickup for notification when drone is destroyed
        if (isSocketPickup && socket != null) {
            drone.markAsSocketPickup(player.getUniqueId(), socket.name());
        }

        droneManager.openDroneInventory(player, drone);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (droneManager.findByEntity(event.getEntity().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (droneManager.findByEntity(event.getEntity().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDroneInventoryClose(InventoryCloseEvent event) {
        DeliveryDrone drone = droneManager.findByInventory(event.getInventory());
        if (drone == null) {
            return;
        }
        if (drone.snapshotItems().isEmpty()) {
            // Check if this was a socket pickup and send notifications
            if (drone.isSocketPickup()) {
                DeliverySocket socket = socketRepository.getAllSockets().stream()
                        .filter(s -> s.name().equals(drone.socketPickupSocketName()))
                        .findFirst()
                        .orElse(null);
                if (socket != null) {
                    Player pickupPlayer = Bukkit.getPlayer(drone.socketPickupPlayerId());
                    if (pickupPlayer != null) {
                        droneManager.sendSocketPickupNotifications(pickupPlayer, drone, socket);
                    }
                }
            }
            droneManager.destroyDrone(drone, false);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        droneManager.deliverPendingReturns(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        java.util.UUID playerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(droneManager.plugin(), () -> {
            if (droneManager.plugin().isEnabled() && Bukkit.getPlayer(playerId) == null) {
                droneManager.receiverWentOffline(playerId);
            }
        }, 20L);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        droneManager.receiverChangedDimension(event.getPlayer().getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Block helmet manipulation for flying or animating drones
        if (event.getSlotType() != InventoryType.SlotType.ARMOR && 
            event.getSlot() != EquipmentSlot.HEAD.ordinal()) {
            return;
        }
        
        // Check if this is an armor stand inventory
        if (event.getClickedInventory() == null || 
            event.getClickedInventory().getHolder() == null) {
            return;
        }
        
        // Check if the holder is an armor stand that belongs to a drone
        if (event.getClickedInventory().getHolder() instanceof org.bukkit.entity.ArmorStand) {
            org.bukkit.entity.ArmorStand armorStand = (org.bukkit.entity.ArmorStand) event.getClickedInventory().getHolder();
            DeliveryDrone drone = droneManager.findByEntity(armorStand.getUniqueId());
            
            if (drone != null && blocksArmorStandInteraction(drone)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player) {
                    Player player = (Player) event.getWhoClicked();
                    droneManager.sendMessage(player, "drone-helmet-remove");
                }
                return;
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        // Block dragging items onto/off drone helmet while flying or animating
        if (event.getInventory().getHolder() instanceof org.bukkit.entity.ArmorStand) {
            org.bukkit.entity.ArmorStand armorStand = (org.bukkit.entity.ArmorStand) event.getInventory().getHolder();
            DeliveryDrone drone = droneManager.findByEntity(armorStand.getUniqueId());
            
            if (drone != null && blocksArmorStandInteraction(drone)) {
                // Check if any slot involved is the helmet slot
                for (Integer slot : event.getInventorySlots()) {
                    if (slot == EquipmentSlot.HEAD.ordinal()) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player) {
                            Player player = (Player) event.getWhoClicked();
                            droneManager.sendMessage(player, "drone-helmet-manipulate");
                        }
                        return;
                    }
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerArmorStandManipulate(org.bukkit.event.player.PlayerArmorStandManipulateEvent event) {
        // Block direct armor manipulation of flying or animating drones
        DeliveryDrone drone = droneManager.findByEntity(event.getRightClicked().getUniqueId());
        
        if (drone != null && blocksArmorStandInteraction(drone)) {
            event.setCancelled(true);
            droneManager.sendMessage(event.getPlayer(), "drone-armor-manipulate");
        }
    }

    private static boolean blocksArmorStandInteraction(DeliveryDrone drone) {
        return drone.isFlying() || drone.isAnimating();
    }
}
