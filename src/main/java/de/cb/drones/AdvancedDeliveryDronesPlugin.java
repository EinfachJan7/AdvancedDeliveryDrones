package de.cb.drones;

import de.cb.drones.command.DroneCommand;
import de.cb.drones.config.LanguageManager;
import de.cb.drones.config.PlayerBlacklistRepository;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.config.SocketPendingReturnsRepository;
import de.cb.drones.discord.DiscordWebhookManager;
import de.cb.drones.drone.DroneInteractionListener;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.socket.SocketRepository;
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
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveGuiConfig();
        this.languageManager = new LanguageManager(this);
        this.languageManager.reload();
        this.playerSettings = new PlayerSettingsRepository(this);
        this.blacklistRepository = new PlayerBlacklistRepository(this);
        this.socketPendingReturns = new SocketPendingReturnsRepository(this);
        int maxSockets = getConfig().getInt("settings.drone.max-sockets-per-player", 3);
        this.socketRepository = new SocketRepository(this, maxSockets);
        this.discordWebhookManager = new DiscordWebhookManager(this);
        this.guiConfig = loadGuiConfig();
        this.droneManager = new DroneManager(
                this,
                DroneSettings.fromConfig(getConfig(), guiConfig),
                discordWebhookManager,
                socketRepository,
                socketPendingReturns
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
    }

    @Override
    public void onDisable() {
        if (droneManager != null) {
            droneManager.shutdown();
        }
    }

    public void reloadPlugin() {
        saveDefaultConfig();
        saveGuiConfig();
        reloadConfig();
        if (this.languageManager == null) {
            this.languageManager = new LanguageManager(this);
        }
        this.languageManager.reload();
        playerSettings.reload();
        blacklistRepository.reload();
        socketPendingReturns.reload();
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

    public DiscordWebhookManager getDiscordWebhookManager() {
        return discordWebhookManager;
    }

    public PlayerBlacklistRepository getBlacklistRepository() {
        return blacklistRepository;
    }
}
