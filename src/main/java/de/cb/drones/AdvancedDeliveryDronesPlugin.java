package de.cb.drones;

import de.cb.drones.command.DroneCommand;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.discord.DiscordWebhookManager;
import de.cb.drones.drone.DroneInteractionListener;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdvancedDeliveryDronesPlugin extends JavaPlugin {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private PlayerSettingsRepository playerSettings;
    private DroneManager droneManager;
    private DiscordWebhookManager discordWebhookManager;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.playerSettings = new PlayerSettingsRepository(this);
        this.discordWebhookManager = new DiscordWebhookManager(this);
        this.droneManager = new DroneManager(this, DroneSettings.fromConfig(getConfig()), discordWebhookManager);
        this.droneManager.start();

        
        DroneCommand command = new DroneCommand(this, droneManager, playerSettings, droneManager.settings());
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
        reloadConfig();
        playerSettings.reload();
        discordWebhookManager.loadSettings();
        droneManager.updateSettings(DroneSettings.fromConfig(getConfig()));
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
