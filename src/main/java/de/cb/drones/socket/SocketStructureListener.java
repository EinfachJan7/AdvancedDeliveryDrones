package de.cb.drones.socket;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener for socket structure selection using the Carrot on a Stick tool.
 * Players use it to mark two corners of a structure.
 */
public class SocketStructureListener implements Listener {
    
    private final JavaPlugin plugin;
    private final Map<UUID, Location> corner1 = new HashMap<>();
    private final Map<UUID, Location> corner2 = new HashMap<>();
    private static final Material TOOL_MATERIAL = Material.CARROT_ON_A_STICK;
    
    public SocketStructureListener(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Mark the first corner.
     */
    public void setCorner1(UUID playerId, Location location) {
        corner1.put(playerId, location.clone());
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Component.text("Corner 1 marked at: " + formatLocation(location), NamedTextColor.GREEN));
            spawnParticles(location);
        }
    }
    
    /**
     * Mark the second corner.
     */
    public void setCorner2(UUID playerId, Location location) {
        corner2.put(playerId, location.clone());
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Component.text("Corner 2 marked at: " + formatLocation(location), NamedTextColor.GREEN));
            spawnParticles(location);
        }
    }
    
    /**
     * Get marked corners.
     */
    public Location getCorner1(UUID playerId) {
        return corner1.get(playerId);
    }
    
    public Location getCorner2(UUID playerId) {
        return corner2.get(playerId);
    }
    
    /**
     * Check if both corners are marked.
     */
    public boolean hasBothCorners(UUID playerId) {
        return corner1.containsKey(playerId) && corner2.containsKey(playerId);
    }
    
    /**
     * Clear marked corners.
     */
    public void clearCorners(UUID playerId) {
        corner1.remove(playerId);
        corner2.remove(playerId);
    }
    
    /**
     * Handle tool interactions.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item.getType() != TOOL_MATERIAL) {
            return;
        }
        
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            event.setCancelled(true);
            setCorner1(player.getUniqueId(), event.getClickedBlock().getLocation());
            return;
        }
        
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            event.setCancelled(true);
            setCorner2(player.getUniqueId(), event.getClickedBlock().getLocation());
        }
    }
    
    /**
     * Spawn particle effects at a location.
     */
    private void spawnParticles(Location location) {
        location.getWorld().spawnParticle(Particle.END_ROD, location.add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2, 0.1);
    }
    
    /**
     * Format location for display.
     */
    private String formatLocation(Location loc) {
        return String.format("X: %.0f, Y: %.0f, Z: %.0f", loc.getX(), loc.getY(), loc.getZ());
    }
}
