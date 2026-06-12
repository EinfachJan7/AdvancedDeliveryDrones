package de.cb.drones.config;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.drone.DeliveryDrone;
import de.cb.drones.drone.DroneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MysqlDronePersistence implements DronePersistence {

    private final AdvancedDeliveryDronesPlugin plugin;
    private final DatabaseManager dbManager;
    private final String tableName;

    public MysqlDronePersistence(AdvancedDeliveryDronesPlugin plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.tableName = dbManager.getTablePrefix() + "drones";
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        if (!dbManager.isConnected()) return;
        String query = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "drone_id VARCHAR(36) PRIMARY KEY, " +
                "data LONGTEXT NOT NULL" +
                ");";
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(query);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create MySQL table " + tableName);
            e.printStackTrace();
        }
    }

    @Override
    public void saveDrones(Collection<DeliveryDrone> drones) {
        if (!dbManager.isConnected()) return;

        long currentTick = Bukkit.getCurrentTick();
        String query = "REPLACE INTO " + tableName + " (drone_id, data) VALUES (?, ?)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            int count = 0;
            for (DeliveryDrone drone : drones) {
                YamlConfiguration config = new YamlConfiguration();
                
                String path = "drone";
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
                if (drone.attachedAnimalSnapshots() != null && !drone.attachedAnimalSnapshots().isEmpty()) {
                    config.set(path + ".attachedAnimalSnapshots", drone.attachedAnimalSnapshots());
                }
                config.set(path + ".animalsOnlyDelivery", drone.animalsOnlyDelivery());
                
                config.set(path + ".forceTargetChunkLoad", drone.isForceTargetChunkLoad());
                config.set(path + ".exactSocketTarget", drone.isExactSocketTarget());
                config.set(path + ".socketName", drone.socketName());
                config.set(path + ".cancelId", drone.getCancelId());
                
                config.set(path + ".landed", drone.isLanded());
                config.set(path + ".openedByReceiver", drone.wasOpenedByReceiver());
                
                long elapsedInteractionTicks = currentTick - drone.lastInteractionTick();
                config.set(path + ".elapsedInteractionTicks", elapsedInteractionTicks);
                
                config.set(path + ".standParked", drone.isStandParked());
                config.set(path + ".returningToSender", drone.isReturningToSender());
                config.set(path + ".originalSendLocation", drone.originalSendLocation());

                String yamlData = config.saveToString();

                pstmt.setString(1, drone.droneId().toString());
                pstmt.setString(2, yamlData);
                pstmt.addBatch();
                count++;
            }
            
            pstmt.executeBatch();
            plugin.getLogger().info("Saved " + count + " active drones to MySQL (" + tableName + ")");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save drones to MySQL!");
            e.printStackTrace();
        }
    }

    @Override
    public void loadDrones(DroneManager droneManager) {
        if (!dbManager.isConnected()) return;
        
        long currentTick = Bukkit.getCurrentTick();
        int loaded = 0;
        
        String selectQuery = "SELECT drone_id, data FROM " + tableName;
        String deleteQuery = "DELETE FROM " + tableName;

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectQuery)) {

            while (rs.next()) {
                String droneIdStr = rs.getString("drone_id");
                String yamlData = rs.getString("data");

                try {
                    YamlConfiguration config = new YamlConfiguration();
                    config.loadFromString(yamlData);

                    String path = "drone";
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
                    List<String> attachedAnimalSnapshots = config.getStringList(path + ".attachedAnimalSnapshots");
                    boolean animalsOnlyDelivery = config.getBoolean(path + ".animalsOnlyDelivery");
                    
                    boolean forceTargetChunkLoad = config.getBoolean(path + ".forceTargetChunkLoad");
                    boolean exactSocketTarget = config.getBoolean(path + ".exactSocketTarget");
                    String socketName = config.getString(path + ".socketName");
                    int cancelId = config.getInt(path + ".cancelId", -1);
                    
                    boolean landed = config.getBoolean(path + ".landed");
                    boolean openedByReceiver = config.getBoolean(path + ".openedByReceiver");
                    
                    long elapsedInteractionTicks = config.getLong(path + ".elapsedInteractionTicks");
                    long lastInteractionTick = currentTick - elapsedInteractionTicks;
                    
                    boolean standParked = config.getBoolean(path + ".standParked");
                    boolean returningToSender = config.getBoolean(path + ".returningToSender");
                    Location originalSendLocation = config.getLocation(path + ".originalSendLocation");
                    
                    DeliveryDrone drone = DeliveryDrone.fromPersistentData(
                        droneId, senderId, receiverId, receiverName, fixedTarget, startLocation,
                        lastKnownLocation, flightStartTick, deliveryFlightStartTick, items, attachedAnimalTypes, attachedAnimalSnapshots,
                        animalsOnlyDelivery, forceTargetChunkLoad, exactSocketTarget, socketName,
                        landed, openedByReceiver, lastInteractionTick, standParked, cancelId, droneManager
                    );
                    if (returningToSender) {
                        drone.restoreReturnFlightState(true, originalSendLocation != null ? originalSendLocation : startLocation);
                    }
                    
                    droneManager.addLoadedDrone(drone);
                    loaded++;

                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to load drone " + droneIdStr + " from MySQL");
                    e.printStackTrace();
                }
            }

            // After successful loading, clear the table just like drones.yml is deleted
            try (Statement deleteStmt = conn.createStatement()) {
                deleteStmt.executeUpdate(deleteQuery);
                plugin.getLogger().info("Successfully loaded " + loaded + " drones. Cleared " + tableName + " table.");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load drones from MySQL!");
            e.printStackTrace();
        }
    }

    @Override
    public void deleteDrone(UUID droneId) {
        if (!dbManager.isConnected()) return;
        String query = "DELETE FROM " + tableName + " WHERE drone_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, droneId.toString());
            pstmt.executeUpdate();
        } catch (Exception ignored) {}
    }
}
