package de.cb.drones.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigUpdater {

    public static void update(JavaPlugin plugin, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        InputStream defConfigStream = plugin.getResource(fileName);
        if (defConfigStream == null) {
            return;
        }

        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
        boolean updated = false;

        for (String key : defConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defConfig.get(key));
                try {
                    if (defConfig.getComments(key) != null && !defConfig.getComments(key).isEmpty()) {
                        config.setComments(key, defConfig.getComments(key));
                    }
                    if (defConfig.getInlineComments(key) != null && !defConfig.getInlineComments(key).isEmpty()) {
                        config.setInlineComments(key, defConfig.getInlineComments(key));
                    }
                } catch (NoSuchMethodError ignored) {
                    // Ignored for older versions
                }
                updated = true;
            }
        }

        // Special check for config-version in config.yml
        if (fileName.equals("config.yml") && !config.contains("config-version")) {
            config.set("config-version", 1);
            updated = true;
        }

        if (updated) {
            try {
                config.save(file);
                plugin.getLogger().info("Updated " + fileName + " with new missing values.");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save updated " + fileName + ": " + e.getMessage());
            }
        }
    }
}
