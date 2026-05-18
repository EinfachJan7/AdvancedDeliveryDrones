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

public final class SocketRepository {
    private static final String SOCKETS_PATH = "sockets";

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;
    private int maxSocketsPerPlayer;

    public SocketRepository(JavaPlugin plugin, int maxSocketsPerPlayer) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sockets.yml");
        this.maxSocketsPerPlayer = maxSocketsPerPlayer;
        reload();
    }

    public void setMaxSocketsPerPlayer(int maxSocketsPerPlayer) {
        this.maxSocketsPerPlayer = maxSocketsPerPlayer;
    }

    public int getMaxSocketsPerPlayer() {
        return maxSocketsPerPlayer;
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

        List<DeliverySocket> existingSockets = getSocketsByOwner(ownerId);
        if (existingSockets.size() >= maxSocketsPerPlayer) {
            throw new IllegalArgumentException("Player already has " + existingSockets.size() + " socket(s). Maximum " + maxSocketsPerPlayer + " socket(s) per player.");
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

    public boolean addTrustedPlayer(UUID ownerId, String socketName, UUID playerUuid) {
        DeliverySocket socket = getSocket(ownerId, socketName);
        if (socket == null) {
            return false;
        }
        
        List<UUID> newTrustedPlayers = new ArrayList<>(socket.trustedPlayers());
        if (newTrustedPlayers.contains(playerUuid)) {
            return false; // Already trusted
        }
        newTrustedPlayers.add(playerUuid);
        
        saveSocket(copySocket(socket, newTrustedPlayers, socket.blacklistedPlayers()));
        return true;
    }

    public boolean removeTrustedPlayer(UUID ownerId, String socketName, UUID playerUuid) {
        DeliverySocket socket = getSocket(ownerId, socketName);
        if (socket == null) {
            return false;
        }
        
        List<UUID> newTrustedPlayers = new ArrayList<>(socket.trustedPlayers());
        if (!newTrustedPlayers.contains(playerUuid)) {
            return false; // Not in trusted list
        }
        newTrustedPlayers.remove(playerUuid);
        
        saveSocket(copySocket(socket, newTrustedPlayers, socket.blacklistedPlayers()));
        return true;
    }

    public boolean addBlacklistedPlayer(UUID ownerId, String socketName, UUID playerUuid) {
        DeliverySocket socket = getSocket(ownerId, socketName);
        if (socket == null) {
            return false;
        }

        List<UUID> blacklisted = new ArrayList<>(socket.blacklistedPlayers());
        if (blacklisted.contains(playerUuid)) {
            return false;
        }
        blacklisted.add(playerUuid);
        saveSocket(copySocket(socket, socket.trustedPlayers(), blacklisted));
        return true;
    }

    public boolean removeBlacklistedPlayer(UUID ownerId, String socketName, UUID playerUuid) {
        DeliverySocket socket = getSocket(ownerId, socketName);
        if (socket == null) {
            return false;
        }

        List<UUID> blacklisted = new ArrayList<>(socket.blacklistedPlayers());
        if (!blacklisted.remove(playerUuid)) {
            return false;
        }
        saveSocket(copySocket(socket, socket.trustedPlayers(), blacklisted));
        return true;
    }

    public List<UUID> getBlacklistedPlayers(UUID ownerId, String socketName) {
        DeliverySocket socket = getSocket(ownerId, socketName);
        if (socket == null) {
            return List.of();
        }
        return new ArrayList<>(socket.blacklistedPlayers());
    }

    public boolean isBlacklisted(UUID ownerId, String socketName, UUID senderId) {
        DeliverySocket socket = getSocket(ownerId, socketName);
        return socket != null && socket.blacklistedPlayers().contains(senderId);
    }

    public boolean relocateSocket(UUID ownerId, String socketName, Location newLocation) {
        DeliverySocket socket = getSocket(ownerId, socketName);
        if (socket == null) {
            return false;
        }
        saveSocket(copySocket(socket, socket.trustedPlayers(), socket.blacklistedPlayers(), newLocation.clone()));
        return true;
    }

    public boolean renameSocket(UUID ownerId, String oldName, String newName) {
        DeliverySocket socket = getSocket(ownerId, oldName);
        if (socket == null) {
            return false;
        }
        if (socketNameExists(ownerId, newName)) {
            return false;
        }

        removeSocket(ownerId, oldName);
        DeliverySocket renamed = new DeliverySocket(
                socket.socketId(),
                socket.ownerId(),
                socket.ownerName(),
                newName,
                socket.location(),
                socket.createdTimestamp(),
                socket.trustedPlayers(),
                socket.blacklistedPlayers()
        );
        saveSocket(renamed);
        return true;
    }

    public List<UUID> getTrustedPlayers(UUID ownerId, String socketName) {
        DeliverySocket socket = getSocket(ownerId, socketName);
        if (socket == null) {
            return List.of();
        }
        return new ArrayList<>(socket.trustedPlayers());
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

        // Save trusted players
        List<String> trustedPlayerStrings = socket.trustedPlayers().stream()
                .map(UUID::toString)
                .toList();
        config.set(socketPath + ".trusted-players", trustedPlayerStrings);

        List<String> blacklistedPlayerStrings = socket.blacklistedPlayers().stream()
                .map(UUID::toString)
                .toList();
        config.set(socketPath + ".blacklisted-players", blacklistedPlayerStrings);
        
        save();
    }

    private DeliverySocket copySocket(DeliverySocket socket, List<UUID> trustedPlayers, List<UUID> blacklistedPlayers) {
        return copySocket(socket, trustedPlayers, blacklistedPlayers, socket.location());
    }

    private DeliverySocket copySocket(DeliverySocket socket, List<UUID> trustedPlayers, List<UUID> blacklistedPlayers, Location location) {
        return new DeliverySocket(
                socket.socketId(),
                socket.ownerId(),
                socket.ownerName(),
                socket.name(),
                location,
                socket.createdTimestamp(),
                trustedPlayers,
                blacklistedPlayers
        );
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

        // Load trusted players
        List<UUID> trustedPlayers = new ArrayList<>();
        if (config.contains(socketPath + ".trusted-players")) {
            for (String uuidStr : config.getStringList(socketPath + ".trusted-players")) {
                try {
                    trustedPlayers.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in trusted players for socket '" + name + "': " + uuidStr);
                }
            }
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Socket '" + name + "' references unloaded world: " + worldName);
            return null;
        }

        Location location = new Location(world, x, y, z, yaw, pitch);
        List<UUID> blacklistedPlayers = new ArrayList<>();
        if (config.contains(socketPath + ".blacklisted-players")) {
            for (String uuidStr : config.getStringList(socketPath + ".blacklisted-players")) {
                try {
                    blacklistedPlayers.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in blacklisted players for socket '" + name + "': " + uuidStr);
                }
            }
        }

        return new DeliverySocket(socketId, ownerId, ownerName, name, location, created, trustedPlayers, blacklistedPlayers);
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save sockets.yml: " + e.getMessage());
        }
    }
}
