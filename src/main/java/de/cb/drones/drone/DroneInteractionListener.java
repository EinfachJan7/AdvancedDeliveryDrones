package de.cb.drones.drone;

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
import org.bukkit.inventory.ItemStack;

public final class DroneInteractionListener implements Listener {
    private final DroneManager droneManager;

    public DroneInteractionListener(DroneManager droneManager) {
        this.droneManager = droneManager;
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
        DeliveryDrone drone = droneManager.findByEntity(clicked.getUniqueId());
        if (drone == null) {
            return false;
        }
        if (!drone.receiverId().equals(player.getUniqueId())) {
            player.sendMessage(droneManager.message("wrong-user", null, null));
            return true;
        }
        if (droneManager.settings().carryLeashedAnimals() && drone.animalsOnlyDelivery()) {
            droneManager.handleAnimalOnlyInteract(drone);
            droneManager.destroyDrone(drone, false);
            return true;
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
            droneManager.destroyDrone(drone, false);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        droneManager.deliverPendingReturns(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        droneManager.receiverWentOffline(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        droneManager.receiverChangedDimension(event.getPlayer().getUniqueId());
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Block helmet manipulation for flying drones
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
            
            if (drone != null && drone.isFlying()) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player) {
                    Player player = (Player) event.getWhoClicked();
                    player.sendMessage(droneManager.message("drone-helmet-remove", null, null));
                }
                return;
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        // Block dragging items onto/off drone helmet while flying
        if (event.getInventory().getHolder() instanceof org.bukkit.entity.ArmorStand) {
            org.bukkit.entity.ArmorStand armorStand = (org.bukkit.entity.ArmorStand) event.getInventory().getHolder();
            DeliveryDrone drone = droneManager.findByEntity(armorStand.getUniqueId());
            
            if (drone != null && drone.isFlying()) {
                // Check if any slot involved is the helmet slot
                for (Integer slot : event.getInventorySlots()) {
                    if (slot == EquipmentSlot.HEAD.ordinal()) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player) {
                            Player player = (Player) event.getWhoClicked();
                            player.sendMessage(droneManager.message("drone-helmet-manipulate", null, null));
                        }
                        return;
                    }
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerArmorStandManipulate(org.bukkit.event.player.PlayerArmorStandManipulateEvent event) {
        // Block direct armor manipulation of flying drones
        DeliveryDrone drone = droneManager.findByEntity(event.getRightClicked().getUniqueId());
        
        if (drone != null && drone.isFlying()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(droneManager.message("drone-armor-manipulate", null, null));
        }
    }
}
