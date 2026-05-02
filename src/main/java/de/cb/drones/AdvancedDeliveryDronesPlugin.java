package de.cb.drones;

import de.cb.drones.command.DroneCommand;
import de.cb.drones.config.PlayerSettingsRepository;
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
    private DroneManager droneManager;
    private DiscordWebhookManager discordWebhookManager;
    private SocketRepository socketRepository;
    private FileConfiguration guiConfig;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveGuiConfig();
        this.playerSettings = new PlayerSettingsRepository(this);
        this.socketRepository = new SocketRepository(this);
        this.discordWebhookManager = new DiscordWebhookManager(this);
        this.guiConfig = loadGuiConfig();
        this.droneManager = new DroneManager(this, DroneSettings.fromConfig(getConfig(), guiConfig), discordWebhookManager);
        this.droneManager.start();

        DroneCommand command = new DroneCommand(this, droneManager, playerSettings, droneManager.settings(), socketRepository);
        PluginCommand drone = getCommand("drone");
        if (drone != null) {
            drone.setExecutor(command);
            drone.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new DroneInteractionListener(droneManager), this);
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
        socketRepository.reload();
        discordWebhookManager.loadSettings();
        this.guiConfig = loadGuiConfig();
        droneManager.updateSettings(DroneSettings.fromConfig(getConfig(), guiConfig));
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
        return YamlConfiguration.loadConfiguration(guiConfigFile);
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

    public DiscordWebhookManager getDiscordWebhookManager() {
        return discordWebhookManager;
    }
}
