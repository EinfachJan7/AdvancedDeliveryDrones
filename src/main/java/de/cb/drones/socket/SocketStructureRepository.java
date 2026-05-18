package de.cb.drones.socket;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Repository for persisting socket structures to YAML files.
 * Stores and loads socket structures from socket_structures.yml.
 */
public class SocketStructureRepository {
    
    private final File dataFile;
    private final Map<String, SocketStructure> structures = new HashMap<>();
    private FileConfiguration config;
    
    public SocketStructureRepository(File dataFolder) {
        this.dataFile = new File(dataFolder, "socket_structures.yml");
        load();
    }
    
    /**
     * Load all structures from YAML file.
     */
    public void load() {
        structures.clear();
        
        if (!dataFile.exists()) {
            return;
        }
        
        config = YamlConfiguration.loadConfiguration(dataFile);
        
        for (String structureName : config.getKeys(false)) {
            String section = structureName;
            int sizeX = config.getInt(section + ".sizeX", 1);
            int sizeY = config.getInt(section + ".sizeY", 1);
            int sizeZ = config.getInt(section + ".sizeZ", 1);
            
            Map<String, String> blockData = new HashMap<>();
            if (config.isConfigurationSection(section + ".blocks")) {
                for (String coordKey : config.getConfigurationSection(section + ".blocks").getKeys(false)) {
                    String blockDataStr = config.getString(section + ".blocks." + coordKey);
                    blockData.put(coordKey, blockDataStr);
                }
            }
            
            structures.put(structureName, new SocketStructure(structureName, sizeX, sizeY, sizeZ, blockData));
        }
    }
    
    /**
     * Save a structure to the repository.
     */
    public void saveStructure(SocketStructure structure) throws IOException {
        if (config == null) {
            config = new YamlConfiguration();
        }
        
        String section = structure.name();
        config.set(section + ".sizeX", structure.sizeX());
        config.set(section + ".sizeY", structure.sizeY());
        config.set(section + ".sizeZ", structure.sizeZ());
        
        // Clear existing blocks
        if (config.isConfigurationSection(section + ".blocks")) {
            config.set(section + ".blocks", null);
        }
        
        // Save blocks
        for (Map.Entry<String, String> entry : structure.blockData().entrySet()) {
            config.set(section + ".blocks." + entry.getKey(), entry.getValue());
        }
        
        structures.put(structure.name(), structure);
        config.save(dataFile);
    }
    
    /**
     * Get a structure by name.
     */
    public SocketStructure getStructure(String name) {
        return structures.get(name);
    }
    
    /**
     * Get all available structures.
     */
    public Set<String> getStructureNames() {
        return new HashSet<>(structures.keySet());
    }
    
    /**
     * Check if a structure exists.
     */
    public boolean exists(String name) {
        return structures.containsKey(name);
    }
    
    /**
     * Delete a structure.
     */
    public void deleteStructure(String name) throws IOException {
        if (config != null) {
            config.set(name, null);
            config.save(dataFile);
        }
        structures.remove(name);
    }
}
