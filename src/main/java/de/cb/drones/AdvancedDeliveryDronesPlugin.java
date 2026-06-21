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
import de.cb.drones.drone.DroneItemManager;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.gui.AdminDroneMenuGUI;
import de.cb.drones.gui.AdminDroneMenuHandler;
import de.cb.drones.log.DroneLogger;

import de.cb.drones.log.YamlDroneLogger;
import de.cb.drones.socket.SocketRepository;
import de.cb.drones.placeholder.PlaceholderHook;
import de.cb.drones.update.UpdateNotificationListener;
import de.cb.drones.util.map.LiveMapHookManager;
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
    private DroneItemManager droneItemManager;
    private DiscordWebhookManager discordWebhookManager;
    private SocketRepository socketRepository;
    private FileConfiguration guiConfig;
    private DroneCommand droneCommand;
    private LanguageManager languageManager;
    private DatabaseManager databaseManager;
    private DroneLogger droneLogger;
    private ConfigEditorService configEditorService;
    private ConfigEditorGUI configEditorGUI;
    private ConfigEditorHandler configEditorHandler;
    private AdminDroneMenuGUI adminDroneMenuGUI;
    private AdminDroneMenuHandler adminDroneMenuHandler;
    private LiveMapHookManager liveMapHookManager;

    @Override
    public void onLoad() {
        de.cb.drones.util.WorldGuardHook.onLoad(getDataFolder());
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveGuiConfig();
        de.cb.drones.config.ConfigUpdater.update(this, "config.yml");
        de.cb.drones.config.ConfigUpdater.mergeMissing(this, "gui.yml");
        
        // Initialize bStats metrics
        int pluginId = 31663;
        new Metrics(this, pluginId);
        
        this.languageManager = new LanguageManager(this);
        this.languageManager.reload();
        
        this.databaseManager = new de.cb.drones.config.DatabaseManager(this);
        if ("MYSQL".equalsIgnoreCase(getConfig().getString("database.type", "YAML"))) {
            this.databaseManager.connect();
        }
        if (getConfig().getBoolean("settings.drone.logging.enabled", true)) {
            this.droneLogger = new YamlDroneLogger(this);
        } else {
            this.droneLogger = null;
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
        this.droneItemManager = new DroneItemManager(this);
        this.droneManager = new DroneManager(
                this,
                DroneSettings.fromConfig(getConfig(), guiConfig),
                discordWebhookManager,
                socketRepository,
                socketPendingReturns,
                databaseManager,
                droneLogger
        );
        
        de.cb.drones.gui.GuiConfiguration guiConfiguration = new de.cb.drones.gui.GuiConfiguration(guiConfig);
        this.adminDroneMenuGUI = new AdminDroneMenuGUI(this, droneManager, guiConfig);
        this.adminDroneMenuHandler = new AdminDroneMenuHandler(this, droneManager, adminDroneMenuGUI, guiConfig);
        
        de.cb.drones.util.WorldGuardHook.setEnabled(getConfig().getBoolean("hooks.worldguard", true));
        
        this.liveMapHookManager = new LiveMapHookManager(this);
        this.liveMapHookManager.init();
        
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
        getServer().getPluginManager().registerEvents(adminDroneMenuHandler, this);

        // Register update notification listener for player join events
        getServer().getPluginManager().registerEvents(
                new UpdateNotificationListener(this, languageManager, getDescription().getVersion()), this
        );

        PlaceholderHook.register(this);
    }

    @Override
    public void onDisable() {
        if (droneCommand != null) {
            droneCommand.saveComposeDrafts();
        }
        if (droneManager != null) {
            droneManager.shutdown();
        }
        if (liveMapHookManager != null) {
            liveMapHookManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public void reloadPlugin() {
        reloadPlugin(org.bukkit.Bukkit.getConsoleSender());
    }

    public void reloadPlugin(org.bukkit.command.CommandSender sender) {
        String oldDbType = getConfig().getString("database.type", "YAML").toUpperCase();

        saveDefaultConfig();
        saveGuiConfig();
        try {
            de.cb.drones.config.ConfigUpdater.update(this, "config.yml", new java.io.File(getDataFolder(), "config.yml"), "settings.drone.drone-item.crafting");
        } catch (java.io.IOException e) {
            getLogger().severe("Could not update config: " + e.getMessage());
        }
        de.cb.drones.config.ConfigUpdater.mergeMissing(this, "gui.yml");
        reloadConfig();
        
        String newDbType = getConfig().getString("database.type", "YAML").toUpperCase();
        
        if (!oldDbType.equals(newDbType)) {
            getLogger().info("Database type change detected: " + oldDbType + " -> " + newDbType);
            sender.sendMessage(component("convert-start"));
            
            DatabaseManager oldDb = this.databaseManager;
            
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                boolean success = false;
                if (oldDbType.equals("YAML") && newDbType.equals("MYSQL")) {
                    DatabaseManager tempDb = new de.cb.drones.config.DatabaseManager(this);
                    tempDb.connect();
                    if (tempDb.isConnected()) {
                        de.cb.drones.command.DataConverter.convertYamlToMysqlAsync(this, tempDb, sender);
                        tempDb.close();
                        success = true;
                    } else {
                        sender.sendMessage(component("convert-abort-mysql"));
                        getConfig().set("database.type", "YAML");
                        saveConfig();
                    }
                } else if (oldDbType.equals("MYSQL") && newDbType.equals("YAML")) {
                    if (oldDb != null && oldDb.isConnected()) {
                        de.cb.drones.command.DataConverter.convertMysqlToYamlAsync(this, oldDb, sender);
                        success = true;
                    } else {
                        sender.sendMessage(component("convert-abort-yaml"));
                        getConfig().set("database.type", "MYSQL");
                        saveConfig();
                    }
                }
                
                final boolean finalSuccess = success;
                org.bukkit.Bukkit.getScheduler().runTask(this, () -> {
                    if (finalSuccess) {
                        sender.sendMessage(component("convert-finish"));
                    }
                    finalizeReload();
                });
            });
            return;
        }

        finalizeReload();
    }

    private void finalizeReload() {
        if (this.languageManager == null) {
            this.languageManager = new LanguageManager(this);
        }
        this.languageManager.reload();
        
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }
        this.databaseManager = new de.cb.drones.config.DatabaseManager(this);
        if ("MYSQL".equalsIgnoreCase(getConfig().getString("database.type", "YAML"))) {
            this.databaseManager.connect();
        }
        if (getConfig().getBoolean("settings.drone.logging.enabled", true)) {
            this.droneLogger = new YamlDroneLogger(this);
        } else {
            this.droneLogger = null;
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
        if (droneItemManager != null) {
            droneItemManager.loadConfig();
        }
        droneManager.updateSettings(DroneSettings.fromConfig(getConfig(), guiConfig));
        droneManager.updateDatabaseManager(databaseManager);
        droneManager.updateLogger(droneLogger);
        if (droneCommand != null) {
            droneCommand.updateMenuHandlerSettings(droneManager.settings());
            droneCommand.reloadComposeDrafts();
        }
        de.cb.drones.util.WorldGuardHook.setEnabled(getConfig().getBoolean("hooks.worldguard", true));
        if (liveMapHookManager != null) {
            liveMapHookManager.reload();
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

    public DroneItemManager getDroneItemManager() {
        return droneItemManager;
    }

    public DroneManager getDroneManager() {
        return droneManager;
    }

    public PlayerSettingsRepository getPlayerSettings() {
        return playerSettings;
    }

    public SocketPendingReturnsRepository getSocketPendingReturnsRepository() {
        return socketPendingReturns;
    }

    public DroneCommand getDroneCommand() {
        return droneCommand;
    }

    public SocketRepository getSocketRepository() {
        return socketRepository;
    }

    public ConfigEditorHandler getConfigEditorHandler() {
        return configEditorHandler;
    }

    public AdminDroneMenuGUI getAdminDroneMenuGUI() {
        return adminDroneMenuGUI;
    }
}
