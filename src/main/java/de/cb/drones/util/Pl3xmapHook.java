package de.cb.drones.util;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.drone.DeliveryDrone;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Circle;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Pl3xmapHook {
    private final AdvancedDeliveryDronesPlugin plugin;
    private final String layerName;
    private boolean enabled;
    private final String LAYER_KEY = "advanced_delivery_drones";

    public Pl3xmapHook(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
        this.layerName = plugin.getConfig().getString("hooks.pl3xmap-layer-name", "Delivery Drones");
        this.enabled = plugin.getConfig().getBoolean("hooks.pl3xmap", true);
        
        if (this.enabled && Bukkit.getPluginManager().getPlugin("Pl3xMap") != null) {
            registerLayers();
        } else {
            this.enabled = false;
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
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to unregister Pl3xMap layers: " + e.getMessage());
        }
    }

    public void reload() {
        unregisterLayers();
        this.enabled = plugin.getConfig().getBoolean("hooks.pl3xmap", true);
        if (this.enabled && Bukkit.getPluginManager().getPlugin("Pl3xMap") != null) {
            registerLayers();
        } else {
            this.enabled = false;
        }
    }

    private class DroneLayer extends Layer {
        public DroneLayer(String key, String name) {
            super(key, () -> name);
            // Pl3xMap UI options
            setUpdateInterval(1); // Update every 1 second
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
                if (mapWorld == null) {
                    continue;
                }

                String droneName = "Drone (Sender: " + Bukkit.getOfflinePlayer(drone.senderId()).getName() + ")";
                Point dronePoint = Point.of(currentLoc.getX(), currentLoc.getZ());
                Circle pointMarker = Marker.circle(drone.droneId().toString(), dronePoint, 2.0)
                        .setOptions(net.pl3x.map.core.markers.option.Options.builder()
                                .tooltipDirection(net.pl3x.map.core.markers.option.Tooltip.Direction.TOP)
                                .tooltipContent(droneName)
                                .strokeColor(0xFFFFAA00) // Orange stroke
                                .fillColor(0xFFFFAA00)
                                .build());
                markers.add(pointMarker);

                Location targetLoc = drone.targetLocation();
                if (targetLoc != null && drone.isFlying()) {
                    Point targetPoint = Point.of(targetLoc.getX(), targetLoc.getZ());
                    Polyline lineMarker = Marker.polyline(drone.droneId().toString() + "_path", dronePoint, targetPoint)
                            .setOptions(net.pl3x.map.core.markers.option.Options.builder()
                                    .strokeColor(0xFFFF0000) // Red
                                    .strokeWeight(2)
                                    .build());
                    markers.add(lineMarker);
                }
            }
            return markers;
        }
    }
}
