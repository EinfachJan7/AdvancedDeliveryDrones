package de.cb.drones.update;

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
    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/advanceddeliverydrones";
    private static final String MODRINTH_LINK = "https://modrinth.com/plugin/advanceddeliverydrones";

    private final JavaPlugin plugin;
    private final String currentVersion;

    public VersionChecker(JavaPlugin plugin, String currentVersion) {
        this.plugin = plugin;
        this.currentVersion = currentVersion;
    }

    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String latestVersion = fetchLatestVersion();
                if (latestVersion != null && !latestVersion.equals(currentVersion)) {
                    notifyAdmins(latestVersion);
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

        // Extract version from JSON: "version":"1.0.4"
        Pattern pattern = Pattern.compile("\"version\":\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(response.toString());
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private void notifyAdmins(String latestVersion) {
        String message = String.format(
                "§e§lAdvancedDeliveryDrones§r §eUpdate available!\n" +
                "§eYour version: §f%s\n" +
                "§eLatest version: §f%s\n" +
                "§eDownload at: §f%s",
                currentVersion, latestVersion, MODRINTH_LINK
        );

        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.getOnlinePlayers().forEach(player -> {
                if (player.hasPermission("drone.admin.update-notify")) {
                    player.sendMessage(message);
                }
            });
            plugin.getLogger().info("Update available: " + latestVersion + " (current: " + currentVersion + ")");
        });
    }
}
