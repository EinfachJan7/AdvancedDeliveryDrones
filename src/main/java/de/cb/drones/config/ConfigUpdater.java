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
        
        config.setDefaults(defConfig);
        config.options().copyDefaults(true);

        // Special check for config-version in config.yml
        if (fileName.equals("config.yml") && !config.isSet("config-version")) {
            config.set("config-version", 1);
        }

        try {
            config.save(file);
            plugin.getLogger().info("Verified and updated " + fileName);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save updated " + fileName + ": " + e.getMessage());
        }
    }
}
