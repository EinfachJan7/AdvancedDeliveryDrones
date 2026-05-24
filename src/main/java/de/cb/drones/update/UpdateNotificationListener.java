package de.cb.drones.update;

import de.cb.drones.config.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class UpdateNotificationListener implements Listener {
    private static final String MODRINTH_LINK = "https://modrinth.com/plugin/advanceddeliverydrones";

    private final JavaPlugin plugin;
    private final LanguageManager languageManager;
    private final MiniMessage miniMessage;
    private String latestVersion;
    private String currentVersion;
    private boolean updateAvailable = false;

    public UpdateNotificationListener(JavaPlugin plugin, LanguageManager languageManager, String currentVersion) {
        this.plugin = plugin;
        this.languageManager = languageManager;
        this.miniMessage = MiniMessage.miniMessage();
        this.currentVersion = currentVersion;
        checkForUpdates();
    }

    private void checkForUpdates() {
        if (!plugin.getConfig().getBoolean("plugin.check-updates", true)) {
            return;
        }

        // Check in async thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                VersionChecker checker = new VersionChecker(plugin, currentVersion, languageManager);
                String latest = checker.fetchLatestVersionPublic();
                if (latest != null && !latest.equals(currentVersion)) {
                    this.latestVersion = latest;
                    this.updateAvailable = true;
                    plugin.getLogger().info("Update available: " + latest + " (current: " + currentVersion + ")");
                } else if (latest != null) {
                    plugin.getLogger().info("AdvancedDeliveryDrones is up to date! (version: " + currentVersion + ")");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateAvailable || !event.getPlayer().hasPermission("drone.admin.update-notify")) {
            return;
        }

        // Delay message sending by 1 tick to ensure player is fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                // Get language strings and replace placeholders
                String prefixStr = languageManager.getString("prefix", "<gold><bold>DRONE</bold></gold> <dark_gray>»</dark_gray> ");
                String updateAvailableStr = languageManager.getString("update-available", "<gold><bold>Update available!</bold></gold>");
                String currentVersionStr = languageManager.getString("update-current-version", "<gray>Current: <white><current></white></gray>")
                        .replace("<current>", currentVersion);
                String latestVersionStr = languageManager.getString("update-latest-version", "<gray>Latest: <white><latest></white></gray>")
                        .replace("<latest>", latestVersion);
                String downloadStr = languageManager.getString("update-download", "<gold>Download: <click:open_url:'<link>'><aqua><link></aqua></click></gold>")
                        .replace("<link>", MODRINTH_LINK);

                // Send each line separately with proper formatting
                Component titleMessage = miniMessage.deserialize(prefixStr + updateAvailableStr);
                Component currentVersionMessage = miniMessage.deserialize(prefixStr + currentVersionStr);
                Component latestVersionMessage = miniMessage.deserialize(prefixStr + latestVersionStr);
                Component downloadMessage = miniMessage.deserialize(prefixStr + downloadStr);

                event.getPlayer().sendMessage(titleMessage);
                event.getPlayer().sendMessage(currentVersionMessage);
                event.getPlayer().sendMessage(latestVersionMessage);
                event.getPlayer().sendMessage(downloadMessage);
            } catch (Exception e) {
                plugin.getLogger().warning("Error sending update notification to player: " + e.getMessage());
            }
        }, 1);
    }
}
