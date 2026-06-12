package de.cb.drones.util;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.drone.DeliveryDrone;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Circle;
import net.pl3x.map.core.markers.marker.Icon;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Pl3xmapHook {
    private final AdvancedDeliveryDronesPlugin plugin;
    private String layerName;
    private boolean enabled;
    
    private boolean useCustomIcon;
    private String iconFilename;
    private int markerColor;
    private double markerRadius;
    
    private String tooltipFormat;
    
    private boolean flightPathEnabled;
    private int flightPathColor;
    private int flightPathWeight;
    
    private final String LAYER_KEY = "advanced_delivery_drones";
    private final String ICON_KEY = "advanced_delivery_drones_icon";

    public Pl3xmapHook(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
        loadSettings();
    }
    
    private void loadSettings() {
        if (plugin.getConfig().isConfigurationSection("hooks.pl3xmap")) {
            this.enabled = plugin.getConfig().getBoolean("hooks.pl3xmap.enabled", true);
            this.layerName = plugin.getConfig().getString("hooks.pl3xmap.layer-name", "Delivery Drones");
            this.useCustomIcon = plugin.getConfig().getBoolean("hooks.pl3xmap.marker.use-custom-icon", false);
            this.iconFilename = plugin.getConfig().getString("hooks.pl3xmap.marker.icon-filename", "drone.png");
            this.markerColor = parseHexColor(plugin.getConfig().getString("hooks.pl3xmap.marker.color", "#FFAA00"));
            this.markerRadius = plugin.getConfig().getDouble("hooks.pl3xmap.marker.radius", 2.0);
            this.tooltipFormat = plugin.getConfig().getString("hooks.pl3xmap.marker.tooltip", "Drone (<status>)<br>Sender: <sender><br>Target: <receiver>");
            
            this.flightPathEnabled = plugin.getConfig().getBoolean("hooks.pl3xmap.flight-path.enabled", true);
            this.flightPathColor = parseHexColor(plugin.getConfig().getString("hooks.pl3xmap.flight-path.color", "#FF0000"));
            this.flightPathWeight = plugin.getConfig().getInt("hooks.pl3xmap.flight-path.weight", 2);
        } else {
            // Fallback for old config
            this.enabled = plugin.getConfig().getBoolean("hooks.pl3xmap", true);
            this.layerName = plugin.getConfig().getString("hooks.pl3xmap-layer-name", "Delivery Drones");
            this.useCustomIcon = false;
            this.markerColor = 0xFFFFAA00;
            this.markerRadius = 2.0;
            this.tooltipFormat = "Drone (<status>)<br>Sender: <sender><br>Target: <receiver>";
            this.flightPathEnabled = true;
            this.flightPathColor = 0xFFFF0000;
            this.flightPathWeight = 2;
        }

        File pl3xmapFolder = new File(plugin.getDataFolder(), "pl3xmap");

        if (this.enabled && Bukkit.getPluginManager().getPlugin("Pl3xMap") != null) {
            if (!pl3xmapFolder.exists()) {
                pl3xmapFolder.mkdirs();
            }
            if (this.useCustomIcon) {
                registerIcon(pl3xmapFolder);
            }
            registerLayers();
        } else {
            this.enabled = false;
            if (pl3xmapFolder.exists()) {
                deleteDirectory(pl3xmapFolder);
            }
        }
    }
    
    private void registerIcon(File folder) {
        File iconFile = new File(folder, iconFilename);
        if (iconFile.exists()) {
            try {
                BufferedImage image = ImageIO.read(iconFile);
                if (image != null) {
                    Pl3xMap.api().getIconRegistry().register(ICON_KEY, new IconImage(ICON_KEY, image, "png"));
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load Pl3xmap icon: " + e.getMessage());
                this.useCustomIcon = false;
            }
        } else {
            plugin.getLogger().warning("Pl3xmap custom icon enabled but file not found: " + iconFile.getPath());
            this.useCustomIcon = false;
        }
    }

    private void registerLayers() {
        try {
            for (World mapWorld : Pl3xMap.api().getWorldRegistry()) {
                if (mapWorld.getLayerRegistry().has(LAYER_KEY)) {
                    continue;
                }
                DroneLayer layer = new DroneLayer(LAYER_KEY, this.layerName);
                mapWorld.getLayerRegistry().register(layer);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register Pl3xMap layers: " + e.getMessage());
            this.enabled = false;
        }
    }

    public void unregisterLayers() {
        if (!enabled) return;
        try {
            for (World mapWorld : Pl3xMap.api().getWorldRegistry()) {
                if (mapWorld.getLayerRegistry().has(LAYER_KEY)) {
                    mapWorld.getLayerRegistry().unregister(LAYER_KEY);
                }
            }
            if (Pl3xMap.api().getIconRegistry().has(ICON_KEY)) {
                Pl3xMap.api().getIconRegistry().unregister(ICON_KEY);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to unregister Pl3xMap layers: " + e.getMessage());
        }
    }

    public void reload() {
        unregisterLayers();
        loadSettings();
    }

    private int parseHexColor(String hex) {
        if (hex == null || !hex.startsWith("#")) return 0xFFFFAA00;
        try {
            int color = Integer.parseInt(hex.substring(1), 16);
            return 0xFF000000 | color;
        } catch (NumberFormatException e) {
            return 0xFFFFAA00;
        }
    }

    private void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }

    private class DroneLayer extends Layer {
        public DroneLayer(String key, String name) {
            super(key, () -> name);
            setUpdateInterval(1);
            setShowControls(true);
            setDefaultHidden(false);
        }

        @Override
        public Collection<Marker<?>> getMarkers() {
            List<Marker<?>> markers = new ArrayList<>();
            if (plugin.getDroneManager() == null) {
                return markers;
            }

            List<DeliveryDrone> drones = plugin.getDroneManager().activeDronesSnapshot();
            for (DeliveryDrone drone : drones) {
                Location currentLoc = drone.currentLocation();
                if (currentLoc == null || currentLoc.getWorld() == null) continue;
                
                World mapWorld = Pl3xMap.api().getWorldRegistry().get(currentLoc.getWorld().getName());
                if (mapWorld == null) continue;

                String senderName = Bukkit.getOfflinePlayer(drone.senderId()).getName();
                String receiverName = Bukkit.getOfflinePlayer(drone.receiverId()).getName();
                String targetDisplay = drone.socketName() != null ? "Socket: " + drone.socketName() : "Player: " + (receiverName != null ? receiverName : "Unknown");
                String statusName = drone.isFlying() ? "Flying" : "Landed";

                String tooltip = tooltipFormat
                        .replace("<sender>", senderName != null ? senderName : "Unknown")
                        .replace("<receiver>", targetDisplay)
                        .replace("<status>", statusName);

                Point dronePoint = Point.of(currentLoc.getX(), currentLoc.getZ());
                
                Marker<?> droneMarker;
                if (useCustomIcon) {
                    droneMarker = Marker.icon(drone.droneId().toString(), dronePoint, ICON_KEY)
                            .setOptions(net.pl3x.map.core.markers.option.Options.builder()
                                    .tooltipDirection(net.pl3x.map.core.markers.option.Tooltip.Direction.TOP)
                                    .tooltipContent(tooltip)
                                    .build());
                } else {
                    droneMarker = Marker.circle(drone.droneId().toString(), dronePoint, markerRadius)
                            .setOptions(net.pl3x.map.core.markers.option.Options.builder()
                                    .tooltipDirection(net.pl3x.map.core.markers.option.Tooltip.Direction.TOP)
                                    .tooltipContent(tooltip)
                                    .strokeColor(markerColor)
                                    .fillColor(markerColor)
                                    .build());
                }
                markers.add(droneMarker);

                if (flightPathEnabled && drone.isFlying()) {
                    Location targetLoc = drone.targetLocation();
                    if (targetLoc != null) {
                        Point targetPoint = Point.of(targetLoc.getX(), targetLoc.getZ());
                        Polyline lineMarker = Marker.polyline(drone.droneId().toString() + "_path", dronePoint, targetPoint)
                                .setOptions(net.pl3x.map.core.markers.option.Options.builder()
                                        .strokeColor(flightPathColor)
                                        .strokeWeight(flightPathWeight)
                                        .build());
                        markers.add(lineMarker);
                    }
                }
            }
            return markers;
        }
    }
}
