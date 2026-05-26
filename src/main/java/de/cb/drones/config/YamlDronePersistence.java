package de.cb.drones.config;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.drone.DeliveryDrone;
import de.cb.drones.drone.DroneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class YamlDronePersistence implements DronePersistence {

    private final AdvancedDeliveryDronesPlugin plugin;
    private final File dataFile;

    public YamlDronePersistence(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "drones.yml");
    }

    @Override
    public void saveDrones(Collection<DeliveryDrone> drones) {
        YamlConfiguration config = new YamlConfiguration();
        long currentTick = Bukkit.getCurrentTick();
        
        for (DeliveryDrone drone : drones) {
            String path = "drones." + drone.droneId().toString();
            config.set(path + ".senderId", drone.senderId().toString());
            config.set(path + ".receiverId", drone.receiverId().toString());
            config.set(path + ".receiverName", drone.receiverName());
            config.set(path + ".fixedTarget", drone.getFixedTarget());
            config.set(path + ".startLocation", drone.startLocation());
            config.set(path + ".lastKnownLocation", drone.currentLocation());
            
            long elapsedFlightTicks = currentTick - drone.getFlightStartTick();
            config.set(path + ".elapsedFlightTicks", elapsedFlightTicks);
            
            long elapsedDeliveryFlightTicks = drone.getDeliveryFlightStartTick() >= 0 
                ? currentTick - drone.getDeliveryFlightStartTick() 
                : -1L;
            config.set(path + ".elapsedDeliveryFlightTicks", elapsedDeliveryFlightTicks);
            
            config.set(path + ".inventory", drone.inventory().getContents());
            
            List<String> animalTypes = drone.attachedAnimalTypes().stream().map(EntityType::name).collect(Collectors.toList());
            config.set(path + ".attachedAnimalTypes", animalTypes);
            config.set(path + ".animalsOnlyDelivery", drone.animalsOnlyDelivery());
            
            config.set(path + ".forceTargetChunkLoad", drone.isForceTargetChunkLoad());
            config.set(path + ".exactSocketTarget", drone.isExactSocketTarget());
            config.set(path + ".socketName", drone.socketName());
            
            config.set(path + ".landed", drone.isLanded());
            config.set(path + ".openedByReceiver", drone.wasOpenedByReceiver());
            
            long elapsedInteractionTicks = currentTick - drone.lastInteractionTick();
            config.set(path + ".elapsedInteractionTicks", elapsedInteractionTicks);
            
            config.set(path + ".standParked", drone.isStandParked());
        }
        
        try {
            config.save(dataFile);
            plugin.getLogger().info("Saved " + drones.size() + " active drones to drones.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save drones to drones.yml");
            e.printStackTrace();
        }
    }

    @Override
    public void loadDrones(DroneManager droneManager) {
        if (!dataFile.exists()) {
            return;
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection dronesSection = config.getConfigurationSection("drones");
        if (dronesSection == null) {
            return;
        }
        
        long currentTick = Bukkit.getCurrentTick();
        int loaded = 0;
        
        for (String droneIdStr : dronesSection.getKeys(false)) {
            try {
                String path = "drones." + droneIdStr;
                UUID droneId = UUID.fromString(droneIdStr);
                UUID senderId = UUID.fromString(config.getString(path + ".senderId"));
                UUID receiverId = UUID.fromString(config.getString(path + ".receiverId"));
                String receiverName = config.getString(path + ".receiverName");
                Location fixedTarget = config.getLocation(path + ".fixedTarget");
                Location startLocation = config.getLocation(path + ".startLocation");
                Location lastKnownLocation = config.getLocation(path + ".lastKnownLocation");
                
                long elapsedFlightTicks = config.getLong(path + ".elapsedFlightTicks");
                long flightStartTick = currentTick - elapsedFlightTicks;
                
                long elapsedDeliveryFlightTicks = config.getLong(path + ".elapsedDeliveryFlightTicks");
                long deliveryFlightStartTick = elapsedDeliveryFlightTicks >= 0 
                    ? currentTick - elapsedDeliveryFlightTicks 
                    : -1L;
                
                List<?> itemsList = config.getList(path + ".inventory");
                ItemStack[] items = new ItemStack[27];
                if (itemsList != null) {
                    items = itemsList.toArray(new ItemStack[0]);
                }
                
                List<String> animalTypesStr = config.getStringList(path + ".attachedAnimalTypes");
                List<EntityType> attachedAnimalTypes = animalTypesStr.stream().map(EntityType::valueOf).collect(Collectors.toList());
                boolean animalsOnlyDelivery = config.getBoolean(path + ".animalsOnlyDelivery");
                
                boolean forceTargetChunkLoad = config.getBoolean(path + ".forceTargetChunkLoad");
                boolean exactSocketTarget = config.getBoolean(path + ".exactSocketTarget");
                String socketName = config.getString(path + ".socketName");
                
                boolean landed = config.getBoolean(path + ".landed");
                boolean openedByReceiver = config.getBoolean(path + ".openedByReceiver");
                
                long elapsedInteractionTicks = config.getLong(path + ".elapsedInteractionTicks");
                long lastInteractionTick = currentTick - elapsedInteractionTicks;
                
                boolean standParked = config.getBoolean(path + ".standParked");
                
                DeliveryDrone drone = DeliveryDrone.fromPersistentData(
                    droneId, senderId, receiverId, receiverName, fixedTarget, startLocation,
                    lastKnownLocation, flightStartTick, deliveryFlightStartTick, items, attachedAnimalTypes,
                    animalsOnlyDelivery, forceTargetChunkLoad, exactSocketTarget, socketName,
                    landed, openedByReceiver, lastInteractionTick, standParked, droneManager
                );
                
                droneManager.addLoadedDrone(drone);
                loaded++;
                
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load drone " + droneIdStr);
                e.printStackTrace();
            }
        }
        
        if (dataFile.delete()) {
            plugin.getLogger().info("Successfully loaded " + loaded + " drones. Cleared drones.yml file.");
        }
    }

    @Override
    public void deleteDrone(UUID droneId) {
        if (!dataFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        if (config.contains("drones." + droneId.toString())) {
            config.set("drones." + droneId.toString(), null);
            try {
                config.save(dataFile);
            } catch (IOException ignored) {}
        }
    }
}
