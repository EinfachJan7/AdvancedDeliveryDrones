package de.cb.drones.util.map.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.AssetStorage;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import de.bluecolored.bluemap.api.math.Shape;
import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.drone.DeliveryDrone;
import de.cb.drones.util.map.LiveMapHook;
import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BluemapLiveMapHook implements LiveMapHook {
    private static final String MARKER_SET_KEY = "advanced_delivery_drones";
    private static final String ICON_ASSET = "advanced-delivery-drones/drone.png";

    private final AdvancedDeliveryDronesPlugin plugin;
    private String layerName;
    private boolean enabled;

    private boolean useCustomIcon;
    private String iconFilename;
    private Color markerColor;
    private double markerRadius;

    private String tooltipFormat;

    private boolean flightPathEnabled;
    private Color flightPathColor;
    private int flightPathWeight;

    private BlueMapAPI api;
    private BukkitTask updateTask;
    private final Map<String, MarkerSet> markerSets = new HashMap<>();
    private final Consumer<BlueMapAPI> onEnableListener = this::onBlueMapEnable;
    private final Consumer<BlueMapAPI> onDisableListener = this::onBlueMapDisable;

    public BluemapLiveMapHook(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
        BlueMapAPI.onEnable(onEnableListener);
        BlueMapAPI.onDisable(onDisableListener);
        BlueMapAPI.getInstance().ifPresent(onEnableListener);
        loadSettings();
    }

    private void loadSettings() {
        if (plugin.getConfig().isConfigurationSection("hooks.livemap")) {
            this.enabled = plugin.getConfig().getBoolean("hooks.livemap.enabled", true)
                    && "bluemap".equalsIgnoreCase(plugin.getConfig().getString("hooks.livemap.type", "bluemap"));
            this.layerName = plugin.getConfig().getString("hooks.livemap.layer-name", "Delivery Drones");
            this.useCustomIcon = plugin.getConfig().getBoolean("hooks.livemap.marker.use-custom-icon", false);
            this.iconFilename = plugin.getConfig().getString("hooks.livemap.marker.icon-filename", "drone.png");
            this.markerColor = parseHexColor(plugin.getConfig().getString("hooks.livemap.marker.color", "#FFAA00"));
            this.markerRadius = plugin.getConfig().getDouble("hooks.livemap.marker.radius", 2.0);
            this.tooltipFormat = plugin.getConfig().getString("hooks.livemap.marker.tooltip", "Drone (<status>)<br>Sender: <sender><br>Target: <receiver>");

            this.flightPathEnabled = plugin.getConfig().getBoolean("hooks.livemap.flight-path.enabled", true);
            this.flightPathColor = parseHexColor(plugin.getConfig().getString("hooks.livemap.flight-path.color", "#FF0000"));
            this.flightPathWeight = plugin.getConfig().getInt("hooks.livemap.flight-path.weight", 2);
        } else {
            this.enabled = false;
            this.layerName = "Delivery Drones";
            this.useCustomIcon = false;
            this.markerColor = new Color("#FFAA00");
            this.markerRadius = 2.0;
            this.tooltipFormat = "Drone (<status>)<br>Sender: <sender><br>Target: <receiver>";
            this.flightPathEnabled = true;
            this.flightPathColor = new Color("#FF0000");
            this.flightPathWeight = 2;
        }

        File bluemapFolder = new File(plugin.getDataFolder(), "livemap");

        if (this.enabled && Bukkit.getPluginManager().getPlugin("BlueMap") != null) {
            if (!bluemapFolder.exists()) {
                bluemapFolder.mkdirs();
            }
            if (api != null) {
                setupMarkerSets();
                startUpdateTask();
            } else {
                BlueMapAPI.getInstance().ifPresent(this::onBlueMapEnable);
            }
        } else {
            this.enabled = false;
            stopUpdateTask();
            clearMarkerSets();
        }
    }

    private void onBlueMapEnable(BlueMapAPI blueMapApi) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            this.api = blueMapApi;
            if (!enabled) {
                return;
            }
            setupMarkerSets();
            startUpdateTask();
        });
    }

    private void onBlueMapDisable(BlueMapAPI blueMapApi) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            stopUpdateTask();
            clearMarkerSets();
            this.api = null;
        });
    }

    private void setupMarkerSets() {
        if (api == null) {
            return;
        }
        clearMarkerSets();
        for (BlueMapMap map : api.getMaps()) {
            MarkerSet markerSet = MarkerSet.builder()
                    .label(layerName)
                    .toggleable(true)
                    .defaultHidden(false)
                    .build();
            map.getMarkerSets().put(MARKER_SET_KEY, markerSet);
            markerSets.put(map.getId(), markerSet);
            if (useCustomIcon) {
                registerIcon(map.getAssetStorage());
            }
        }
        updateMarkers();
    }

    private void registerIcon(AssetStorage storage) {
        File iconFile = new File(new File(plugin.getDataFolder(), "livemap"), iconFilename);
        if (!iconFile.exists()) {
            plugin.getLogger().warning("BlueMap custom icon enabled but file not found: " + iconFile.getPath());
            useCustomIcon = false;
            return;
        }
        try {
            try (InputStream in = Files.newInputStream(iconFile.toPath());
                 OutputStream out = storage.writeAsset(ICON_ASSET)) {
                in.transferTo(out);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to upload BlueMap icon: " + e.getMessage());
            useCustomIcon = false;
        }
    }

    private void startUpdateTask() {
        stopUpdateTask();
        if (!enabled || api == null) {
            return;
        }
        updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateMarkers, 2L, 2L);
    }

    private void stopUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private void updateMarkers() {
        if (!enabled || api == null) {
            return;
        }
        for (MarkerSet markerSet : markerSets.values()) {
            markerSet.getMarkers().clear();
        }
        if (plugin.getDroneManager() == null) {
            return;
        }

        List<DeliveryDrone> drones = plugin.getDroneManager().activeDronesSnapshot();
        for (DeliveryDrone drone : drones) {
            Location currentLoc = drone.currentLocation();
            if (currentLoc == null || currentLoc.getWorld() == null) {
                continue;
            }

            api.getWorld(currentLoc.getWorld()).ifPresent(blueMapWorld -> {
                for (BlueMapMap map : blueMapWorld.getMaps()) {
                    MarkerSet markerSet = markerSets.get(map.getId());
                    if (markerSet == null) {
                        continue;
                    }
                    addDroneMarkers(map, markerSet, drone, currentLoc);
                }
            });
        }
    }

    private void addDroneMarkers(BlueMapMap map, MarkerSet markerSet, DeliveryDrone drone, Location currentLoc) {
        String senderName = Bukkit.getOfflinePlayer(drone.senderId()).getName();
        String receiverName = Bukkit.getOfflinePlayer(drone.receiverId()).getName();
        String targetDisplay = drone.socketName() != null
                ? "Socket: " + drone.socketName()
                : "Player: " + (receiverName != null ? receiverName : "Unknown");
        String statusName = drone.isFlying() ? "Flying" : "Landed";

        String tooltip = tooltipFormat
                .replace("<sender>", senderName != null ? senderName : "Unknown")
                .replace("<receiver>", targetDisplay)
                .replace("<status>", statusName);

        String droneId = drone.droneId().toString();
        Vector3d position = new Vector3d(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ());

        if (useCustomIcon) {
            POIMarker poiMarker = new POIMarker("Drone", position);
            poiMarker.setDetail(tooltip);
            String iconUrl = map.getAssetStorage().getAssetUrl(ICON_ASSET);
            poiMarker.setIcon(iconUrl, new Vector2i(16, 16));
            markerSet.put(droneId, poiMarker);
        } else {
            Shape circle = Shape.createCircle(currentLoc.getX(), currentLoc.getZ(), markerRadius, 16);
            ShapeMarker shapeMarker = new ShapeMarker("Drone", position, circle, (float) currentLoc.getY());
            shapeMarker.setColors(markerColor, markerColor);
            shapeMarker.setDetail(tooltip);
            markerSet.put(droneId, shapeMarker);
        }

        if (flightPathEnabled && drone.isFlying()) {
            Location targetLoc = drone.targetLocation();
            if (targetLoc != null) {
                Line line = Line.builder()
                        .addPoint(new Vector3d(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ()))
                        .addPoint(new Vector3d(targetLoc.getX(), targetLoc.getY(), targetLoc.getZ()))
                        .build();
                LineMarker lineMarker = new LineMarker("Flight Path", line);
                lineMarker.setLineColor(flightPathColor);
                lineMarker.setLineWidth(flightPathWeight);
                markerSet.put(droneId + "_path", lineMarker);
            }
        }
    }

    private void clearMarkerSets() {
        if (api != null) {
            try {
                for (BlueMapMap map : api.getMaps()) {
                    map.getMarkerSets().remove(MARKER_SET_KEY);
                }
            } catch (Exception e) {
                // Ignore if maps are already invalid
            }
        }
        markerSets.clear();
    }

    @Override
    public void reload() {
        stopUpdateTask();
        clearMarkerSets();
        loadSettings();
    }

    @Override
    public void shutdown() {
        stopUpdateTask();
        clearMarkerSets();
        BlueMapAPI.unregisterListener(onEnableListener);
        BlueMapAPI.unregisterListener(onDisableListener);
    }

    private Color parseHexColor(String hex) {
        if (hex == null || !hex.startsWith("#")) {
            return new Color("#FFAA00");
        }
        try {
            return new Color(hex);
        } catch (NumberFormatException e) {
            return new Color("#FFAA00");
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
}
