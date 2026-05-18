package de.cb.drones.socket;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Manages preview block displays for socket structures.
 * Shows AQUA glowing blocks when player places a socket.
 */
public class SocketPreviewManager {
    
    private final JavaPlugin plugin;
    private final Map<UUID, List<BlockDisplay>> activeDisplays = new HashMap<>();
    
    public SocketPreviewManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Show a structure preview at the given location.
     */
    public void showPreview(UUID playerId, Location origin, SocketStructure structure) {
        clearPreview(playerId);
        
        List<BlockDisplay> displays = new ArrayList<>();
        
        for (String coordKey : structure.getCoordinates()) {
            String[] parts = coordKey.split(",");
            if (parts.length != 3) continue;
            
            try {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                
                Location blockLoc = origin.clone().add(x, y, z);
                
                BlockDisplay display = origin.getWorld().spawn(blockLoc.add(0.5, 0, 0.5), BlockDisplay.class, entity -> {
                    entity.setBlock(structure.getBlockData(x, y, z).getMaterial().createBlockData());
                    entity.setGlowing(true);
                    entity.setViewRange(1000f);
                    
                    // Set AQUA glow color
                    entity.setGlowColorOverride(Color.AQUA);
                });
                
                displays.add(display);
            } catch (NumberFormatException e) {
                // Skip invalid coordinates
            }
        }
        
        activeDisplays.put(playerId, displays);
    }
    
    /**
     * Clear preview for a player.
     */
    public void clearPreview(UUID playerId) {
        List<BlockDisplay> displays = activeDisplays.remove(playerId);
        if (displays != null) {
            for (Entity entity : displays) {
                entity.remove();
            }
        }
    }
    
    /**
     * Clear all previews.
     */
    public void clearAllPreviews() {
        for (List<BlockDisplay> displays : activeDisplays.values()) {
            for (Entity entity : displays) {
                entity.remove();
            }
        }
        activeDisplays.clear();
    }
    
    /**
     * Check if a player has an active preview.
     */
    public boolean hasActivePreview(UUID playerId) {
        return activeDisplays.containsKey(playerId) && !activeDisplays.get(playerId).isEmpty();
    }
}
