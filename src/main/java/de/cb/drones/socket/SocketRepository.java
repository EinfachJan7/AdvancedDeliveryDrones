package de.cb.drones.socket;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SocketRepository {
    private static final String SOCKETS_PATH = "sockets";

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public SocketRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sockets.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Could not create sockets.yml", e);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public DeliverySocket addSocket(UUID ownerId, String ownerName, String name, Location location) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Socket name cannot be null or blank");
        }
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }

        // Max 1 socket per player
        List<DeliverySocket> existingSockets = getSocketsByOwner(ownerId);
        if (!existingSockets.isEmpty()) {
            throw new IllegalArgumentException("Player already has a socket. Maximum 1 socket per player.");
        }

        String socketPath = SOCKETS_PATH + "." + ownerId + "." + name;
        if (config.contains(socketPath)) {
            throw new IllegalArgumentException("Socket with name '" + name + "' already exists");
        }

        DeliverySocket socket = DeliverySocket.create(ownerId, ownerName, name, location);
        saveSocket(socket);
        return socket;
    }

    public boolean removeSocket(UUID ownerId, String name) {
        String socketPath = SOCKETS_PATH + "." + ownerId + "." + name;
        if (!config.contains(socketPath)) {
            return false;
        }
        config.set(socketPath, null);
        save();
        return true;
    }

    public DeliverySocket getSocket(UUID ownerId, String name) {
        String socketPath = SOCKETS_PATH + "." + ownerId + "." + name;
        if (!config.contains(socketPath)) {
            return null;
        }
        return loadSocket(ownerId, name, socketPath);
    }

    public List<DeliverySocket> getSocketsByOwner(UUID ownerId) {
        String ownerPath = SOCKETS_PATH + "." + ownerId;
        if (!config.contains(ownerPath)) {
            return List.of();
        }

        List<DeliverySocket> sockets = new ArrayList<>();
        for (String socketName : config.getConfigurationSection(ownerPath).getKeys(false)) {
            String socketPath = ownerPath + "." + socketName;
            DeliverySocket socket = loadSocket(ownerId, socketName, socketPath);
            if (socket != null) {
                sockets.add(socket);
            }
        }
        return sockets;
    }

    public DeliverySocket getSocketById(UUID socketId) {
        for (String ownerKey : config.getConfigurationSection(SOCKETS_PATH).getKeys(false)) {
            UUID ownerId = UUID.fromString(ownerKey);
            String ownerPath = SOCKETS_PATH + "." + ownerKey;
            for (String socketName : config.getConfigurationSection(ownerPath).getKeys(false)) {
                String socketPath = ownerPath + "." + socketName;
                UUID loadedId = UUID.fromString(config.getString(socketPath + ".socket-id"));
                if (loadedId.equals(socketId)) {
                    return loadSocket(ownerId, socketName, socketPath);
                }
            }
        }
        return null;
    }

    public List<DeliverySocket> getAllSockets() {
        if (!config.contains(SOCKETS_PATH)) {
            return List.of();
        }

        List<DeliverySocket> sockets = new ArrayList<>();
        for (String ownerKey : config.getConfigurationSection(SOCKETS_PATH).getKeys(false)) {
            UUID ownerId = UUID.fromString(ownerKey);
            sockets.addAll(getSocketsByOwner(ownerId));
        }
        return sockets;
    }

    public boolean socketNameExists(UUID ownerId, String name) {
        String socketPath = SOCKETS_PATH + "." + ownerId + "." + name;
        return config.contains(socketPath);
    }

    private void saveSocket(DeliverySocket socket) {
        String socketPath = SOCKETS_PATH + "." + socket.ownerId() + "." + socket.name();
        config.set(socketPath + ".socket-id", socket.socketId().toString());
        config.set(socketPath + ".owner-name", socket.ownerName());
        config.set(socketPath + ".world", socket.getWorldName());
        config.set(socketPath + ".x", socket.location().getX());
        config.set(socketPath + ".y", socket.location().getY());
        config.set(socketPath + ".z", socket.location().getZ());
        config.set(socketPath + ".yaw", socket.location().getYaw());
        config.set(socketPath + ".pitch", socket.location().getPitch());
        config.set(socketPath + ".created", socket.createdTimestamp());
        save();
    }

    private DeliverySocket loadSocket(UUID ownerId, String name, String socketPath) {
        UUID socketId = UUID.fromString(config.getString(socketPath + ".socket-id"));
        String ownerName = config.getString(socketPath + ".owner-name");
        String worldName = config.getString(socketPath + ".world");
        double x = config.getDouble(socketPath + ".x");
        double y = config.getDouble(socketPath + ".y");
        double z = config.getDouble(socketPath + ".z");
        float yaw = (float) config.getDouble(socketPath + ".yaw");
        float pitch = (float) config.getDouble(socketPath + ".pitch");
        long created = config.getLong(socketPath + ".created");

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Socket '" + name + "' references unloaded world: " + worldName);
            return null;
        }

        Location location = new Location(world, x, y, z, yaw, pitch);
        return new DeliverySocket(socketId, ownerId, ownerName, name, location, created);
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save sockets.yml: " + e.getMessage());
        }
    }
}
