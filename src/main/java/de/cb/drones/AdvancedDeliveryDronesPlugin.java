package de.cb.drones;

import de.cb.drones.command.DroneCommand;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.discord.DiscordWebhookManager;
import de.cb.drones.drone.DroneInteractionListener;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.socket.SocketRepository;
// import de.cb.drones.socket.SocketBlacklistRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class AdvancedDeliveryDronesPlugin extends JavaPlugin {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private PlayerSettingsRepository playerSettings;
    private DroneManager droneManager;
    private DiscordWebhookManager discordWebhookManager;
    // private SocketBlacklistRepository blacklistRepository;
    private SocketRepository socketRepository;
    private FileConfiguration guiConfig;
    private DroneCommand droneCommand;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveGuiConfig();
        this.playerSettings = new PlayerSettingsRepository(this);
        int maxSockets = getConfig().getInt("settings.drone.max-sockets-per-player", 3);
        this.socketRepository = new SocketRepository(this, maxSockets);
        // this.blacklistRepository = new SocketBlacklistRepository(this);
        this.discordWebhookManager = new DiscordWebhookManager(this);
        this.guiConfig = loadGuiConfig();
        this.droneManager = new DroneManager(this, DroneSettings.fromConfig(getConfig(), guiConfig), discordWebhookManager, socketRepository);
        this.droneManager.start();

        this.droneCommand = new DroneCommand(this, droneManager, playerSettings, droneManager.settings(), socketRepository);
        PluginCommand drone = getCommand("drone");
        if (drone != null) {
            drone.setExecutor(droneCommand);
            drone.setTabCompleter(droneCommand);
        }

        getServer().getPluginManager().registerEvents(new DroneInteractionListener(droneManager, socketRepository), this);
    }

    @Override
    public void onDisable() {
        if (droneManager != null) {
            droneManager.shutdown();
        }
    }

    public void reloadPlugin() {
        saveDefaultConfig(); // Create config if it doesn't exist
        saveGuiConfig(); // Ensure GUI config exists
        reloadConfig();
        playerSettings.reload();
        // blacklistRepository.reload();
        int maxSockets = getConfig().getInt("settings.drone.max-sockets-per-player", 3);
        this.socketRepository.setMaxSocketsPerPlayer(maxSockets);
        this.socketRepository.reload();
        discordWebhookManager.loadSettings();
        this.guiConfig = loadGuiConfig();
        droneManager.updateSettings(DroneSettings.fromConfig(getConfig(), guiConfig));
        if (droneCommand != null) {
            droneCommand.updateMenuHandlerSettings(droneManager.settings());
        }
    }

    private void saveGuiConfig() {
        File guiConfigFile = new File(getDataFolder(), "gui.yml");
        if (!guiConfigFile.exists()) {
            saveResource("gui.yml", false);
        }
    }

    private FileConfiguration loadGuiConfig() {
        File guiConfigFile = new File(getDataFolder(), "gui.yml");
        if (!guiConfigFile.exists()) {
            saveResource("gui.yml", false);
        }
        // Always create a fresh FileConfiguration to avoid caching issues
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(guiConfigFile);
        } catch (Exception e) {
            getLogger().warning("Could not load gui.yml: " + e.getMessage());
            // Return empty config as fallback
        }
        return config;
    }

    public Component component(String key) {
        String prefix = getConfig().getString("messages.prefix", "");
        String body = getConfig().getString("messages." + key, key);
        return miniMessage.deserialize(prefix + body);
    }

    public String message(String key, String placeholder, String value) {
        String prefix = getConfig().getString("messages.prefix", "");
        String body = getConfig().getString("messages." + key, key);
        if (placeholder != null && value != null) {
            body = body.replace(placeholder, value);
        }
        return miniMessage.serialize(miniMessage.deserialize(prefix + body));
    }

    public Component componentMessage(String key, String placeholder, String value) {
        String prefix = getConfig().getString("messages.prefix", "");
        String body = getConfig().getString("messages." + key, key);
        if (placeholder != null && value != null) {
            body = body.replace(placeholder, value);
        }
        return miniMessage.deserialize(prefix + body);
    }

    public DiscordWebhookManager getDiscordWebhookManager() {
        return discordWebhookManager;
    }

    // public SocketBlacklistRepository getBlacklistRepository() {
    //     return blacklistRepository;
    // }
}
