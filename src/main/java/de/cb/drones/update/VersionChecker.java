package de.cb.drones.update;

import de.cb.drones.config.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionChecker {
    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/advanceddeliverydrones/version?include_changelog=false";
    private static final String MODRINTH_LINK = "https://modrinth.com/plugin/advanceddeliverydrones";

    private final JavaPlugin plugin;
    private final String currentVersion;
    private final LanguageManager languageManager;
    private final MiniMessage miniMessage;

    public VersionChecker(JavaPlugin plugin, String currentVersion, LanguageManager languageManager) {
        this.plugin = plugin;
        this.currentVersion = currentVersion;
        this.languageManager = languageManager;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void checkForUpdates() {
        if (!plugin.getConfig().getBoolean("plugin.check-updates", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String latestVersion = fetchLatestVersion();
                if (latestVersion != null) {
                    if (latestVersion.equals(currentVersion)) {
                        plugin.getLogger().info("AdvancedDeliveryDrones is up to date! (version: " + currentVersion + ")");
                    } else {
                        notifyAdmins(latestVersion);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not check for updates: " + e.getMessage());
            }
        });
    }

    private String fetchLatestVersion() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(MODRINTH_API).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "AdvancedDeliveryDrones");

        if (connection.getResponseCode() != 200) {
            throw new Exception("API returned status code: " + connection.getResponseCode());
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        // Extract the first version_number from the array (latest version)
        // Response format: [{"version_number":"1.0.5",...}, {"version_number":"1.0.4",...}, ...]
        Pattern versionPattern = Pattern.compile("\"version_number\":\"([^\"]+)\"");
        Matcher versionMatcher = versionPattern.matcher(response.toString());

        if (versionMatcher.find()) {
            return versionMatcher.group(1);
        }

        return null;
    }

    private void notifyAdmins(String latestVersion) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Get language strings and replace placeholders
            String prefixStr = languageManager.getString("prefix");
            String updateAvailableStr = languageManager.getString("update-available");
            String currentVersionStr = languageManager.getString("update-current-version")
                    .replace("<current>", currentVersion);
            String latestVersionStr = languageManager.getString("update-latest-version")
                    .replace("<latest>", latestVersion);
            String downloadStr = languageManager.getString("update-download")
                    .replace("<link>", MODRINTH_LINK);

            // Combine all messages
            String fullMessage = prefixStr + updateAvailableStr + "\n" +
                    prefixStr + currentVersionStr + "\n" +
                    prefixStr + latestVersionStr + "\n" +
                    prefixStr + downloadStr;

            // Convert to Component and send to admins
            Component message = miniMessage.deserialize(fullMessage);
            Bukkit.getOnlinePlayers().forEach(player -> {
                if (player.hasPermission("drone.admin.update-notify")) {
                    player.sendMessage(message);
                }
            });
            plugin.getLogger().info("Update available: " + latestVersion + " (current: " + currentVersion + ")");
        });
    }
}
