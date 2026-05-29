package de.cb.drones;

import de.cb.drones.command.DroneCommand;
import de.cb.drones.configeditor.ConfigEditorGUI;
import de.cb.drones.configeditor.ConfigEditorGuiSettings;
import de.cb.drones.configeditor.ConfigEditorHandler;
import de.cb.drones.configeditor.ConfigEditorService;
import de.cb.drones.config.DatabaseManager;
import de.cb.drones.config.LanguageManager;
import de.cb.drones.config.PlayerBlacklistRepository;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.config.SocketPendingReturnsRepository;
import de.cb.drones.discord.DiscordWebhookManager;
import de.cb.drones.drone.DroneInteractionListener;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.socket.SocketRepository;
import de.cb.drones.placeholder.PlaceholderHook;
import de.cb.drones.update.UpdateNotificationListener;
import org.bstats.bukkit.Metrics;
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
    private PlayerBlacklistRepository blacklistRepository;
    private SocketPendingReturnsRepository socketPendingReturns;
    private DroneManager droneManager;
    private DiscordWebhookManager discordWebhookManager;
    private SocketRepository socketRepository;
    private FileConfiguration guiConfig;
    private DroneCommand droneCommand;
    private LanguageManager languageManager;
    private DatabaseManager databaseManager;
    private ConfigEditorService configEditorService;
    private ConfigEditorGUI configEditorGUI;
    private ConfigEditorHandler configEditorHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveGuiConfig();
        de.cb.drones.config.ConfigUpdater.update(this, "config.yml");
        de.cb.drones.config.ConfigUpdater.update(this, "gui.yml");
        
        // Initialize bStats metrics
        int pluginId = 23353; // TODO: Replace with your plugin ID from https://bstats.org/what-is-my-plugin-id
        new Metrics(this, pluginId);
        
        this.languageManager = new LanguageManager(this);
        this.languageManager.reload();
        
        this.databaseManager = new de.cb.drones.config.DatabaseManager(this);
        if ("MYSQL".equalsIgnoreCase(getConfig().getString("database.type", "YAML"))) {
            this.databaseManager.connect();
        }
        
        this.playerSettings = new PlayerSettingsRepository(this);
        this.blacklistRepository = new PlayerBlacklistRepository(this);
        this.socketPendingReturns = new SocketPendingReturnsRepository(this);
        int maxSockets = getConfig().getInt("settings.drone.max-sockets-per-player", 3);
        this.socketRepository = new SocketRepository(this, maxSockets);
        this.discordWebhookManager = new DiscordWebhookManager(this);
        this.guiConfig = loadGuiConfig();
        this.configEditorService = new ConfigEditorService(this);
        this.configEditorGUI = new ConfigEditorGUI(this, configEditorService, new ConfigEditorGuiSettings(guiConfig));
        this.configEditorHandler = new ConfigEditorHandler(this, configEditorService, configEditorGUI);
        this.droneManager = new DroneManager(
                this,
                DroneSettings.fromConfig(getConfig(), guiConfig),
                discordWebhookManager,
                socketRepository,
                socketPendingReturns,
                databaseManager
        );
        
        this.droneManager.start();

        this.droneCommand = new DroneCommand(
                this,
                droneManager,
                playerSettings,
                blacklistRepository,
                droneManager.settings(),
                socketRepository
        );
        PluginCommand drone = getCommand("drone");
        if (drone != null) {
            drone.setExecutor(droneCommand);
            drone.setTabCompleter(droneCommand);
        }

        getServer().getPluginManager().registerEvents(new DroneInteractionListener(droneManager, socketRepository), this);

        // Register update notification listener for player join events
        getServer().getPluginManager().registerEvents(
                new UpdateNotificationListener(this, languageManager, getDescription().getVersion()), this
        );

        PlaceholderHook.register(this);
    }

    @Override
    public void onDisable() {
        if (droneManager != null) {
            droneManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public void reloadPlugin() {
        saveDefaultConfig();
        saveGuiConfig();
        de.cb.drones.config.ConfigUpdater.update(this, "config.yml");
        de.cb.drones.config.ConfigUpdater.update(this, "gui.yml");
        reloadConfig();
        if (this.languageManager == null) {
            this.languageManager = new LanguageManager(this);
        }
        this.languageManager.reload();
        
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }
        this.databaseManager = new de.cb.drones.config.DatabaseManager(this);
        if ("MYSQL".equalsIgnoreCase(getConfig().getString("database.type", "YAML"))) {
            if (this.databaseManager.connect()) {
                getLogger().info(languageManager.getString("mysql-connected", "MySQL connected!"));
            } else {
                getLogger().severe(languageManager.getString("mysql-connection-failed", "MySQL connection failed!"));
            }
        }
        
        playerSettings.reload();
        blacklistRepository.reload();
        socketPendingReturns.reload();
        int maxSockets = getConfig().getInt("settings.drone.max-sockets-per-player", 3);
        this.socketRepository.setMaxSocketsPerPlayer(maxSockets);
        this.socketRepository.reload();
        discordWebhookManager.loadSettings();
        this.guiConfig = loadGuiConfig();
        if (configEditorGUI != null) {
            configEditorGUI.reloadSettings(new ConfigEditorGuiSettings(guiConfig));
        }
        if (configEditorHandler != null) {
            configEditorHandler.reloadGuiSettings(new ConfigEditorGuiSettings(guiConfig));
        }
        droneManager.updateSettings(DroneSettings.fromConfig(getConfig(), guiConfig));
        droneManager.updateDatabaseManager(databaseManager);
        if (droneCommand != null) {
            droneCommand.updateMenuHandlerSettings(droneManager.settings());
        }
        PlaceholderHook.register(this);
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
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(guiConfigFile);
        } catch (Exception e) {
            getLogger().warning("Could not load gui.yml: " + e.getMessage());
        }
        return config;
    }

    public Component component(String key) {
        String prefix = languageManager.getString("prefix", "");
        String body = languageManager.getString(key, key);
        return miniMessage.deserialize(prefix + body);
    }

    public String message(String key, String placeholder, String value) {
        String prefix = languageManager.getString("prefix", "");
        String body = languageManager.getString(key, key);
        if (placeholder != null && value != null) {
            body = body.replace(placeholder, value);
        }
        return miniMessage.serialize(miniMessage.deserialize(prefix + body));
    }

    public Component componentMessage(String key, String placeholder, String value) {
        String prefix = languageManager.getString("prefix", "");
        String body = languageManager.getString(key, key);
        if (placeholder != null && value != null) {
            body = body.replace(placeholder, value);
        }
        return miniMessage.deserialize(prefix + body);
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public DiscordWebhookManager getDiscordWebhookManager() {
        return discordWebhookManager;
    }

    public PlayerBlacklistRepository getBlacklistRepository() {
        return blacklistRepository;
    }

    public DroneManager getDroneManager() {
        return droneManager;
    }

    public PlayerSettingsRepository getPlayerSettings() {
        return playerSettings;
    }

    public SocketRepository getSocketRepository() {
        return socketRepository;
    }

    public ConfigEditorHandler getConfigEditorHandler() {
        return configEditorHandler;
    }
}
