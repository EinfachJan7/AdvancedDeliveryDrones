package de.cb.drones.drone;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

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
}
