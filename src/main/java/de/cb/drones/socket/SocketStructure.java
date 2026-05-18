package de.cb.drones.socket;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.*;

/**
 * Record representing a socket structure with relative block coordinates and data.
 * Used to store and recreate socket landing structures.
 */
public record SocketStructure(
        String name,
        int sizeX,
        int sizeY,
        int sizeZ,
        Map<String, String> blockData // Relative coordinate (x,y,z) -> BlockData string
) {
    
    public SocketStructure {
        blockData = new HashMap<>(blockData);
    }
    
    /**
     * Get all block coordinates in this structure.
     */
    public Set<String> getCoordinates() {
        return blockData.keySet();
    }
    
    /**
     * Get BlockData for a relative coordinate.
     */
    public BlockData getBlockData(int x, int y, int z) {
        String key = x + "," + y + "," + z;
        String dataStr = blockData.get(key);
        if (dataStr == null) {
            return Material.AIR.createBlockData();
        }
        try {
            return org.bukkit.Bukkit.createBlockData(dataStr);
        } catch (IllegalArgumentException e) {
            return Material.AIR.createBlockData();
        }
    }
    
    /**
     * Get the dimension of this structure.
     */
    public int getVolume() {
        return sizeX * sizeY * sizeZ;
    }
}
