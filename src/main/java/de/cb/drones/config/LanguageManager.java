package de.cb.drones.config;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LanguageManager {
    private final AdvancedDeliveryDronesPlugin plugin;
    private YamlConfiguration langConfig;
    private String currentLanguage;

    public LanguageManager(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.currentLanguage = plugin.getConfig().getString("language", "de_DE");
        
        File folder = new File(plugin.getDataFolder(), "languages");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        saveDefaultLangFiles(folder);

        File langFile = new File(folder, this.currentLanguage + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file '" + this.currentLanguage + ".yml' not found! Falling back to 'de_DE.yml'.");
            this.currentLanguage = "de_DE";
            langFile = new File(folder, "de_DE.yml");
        }

        this.langConfig = YamlConfiguration.loadConfiguration(langFile);

        try {
            InputStream fallbackStream = plugin.getResource("languages/" + this.currentLanguage + ".yml");
            if (fallbackStream == null) {
                fallbackStream = plugin.getResource("languages/de_DE.yml");
            }
            if (fallbackStream != null) {
                YamlConfiguration fallbackConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(fallbackStream, StandardCharsets.UTF_8));
                this.langConfig.setDefaults(fallbackConfig);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error loading fallback language configuration: " + e.getMessage());
        }
    }

    private void saveDefaultLangFiles(File folder) {
        File deFile = new File(folder, "de_DE.yml");
        if (!deFile.exists()) {
            try {
                plugin.saveResource("languages/de_DE.yml", false);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save languages/de_DE.yml from JAR: " + e.getMessage());
            }
        }
        File enFile = new File(folder, "en_EN.yml");
        if (!enFile.exists()) {
            try {
                plugin.saveResource("languages/en_EN.yml", false);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save languages/en_EN.yml from JAR: " + e.getMessage());
            }
        }
    }

    public String getString(String key, String def) {
        if (langConfig == null) {
            return def;
        }
        return langConfig.getString(key, def);
    }

    public String getString(String key) {
        return getString(key, key);
    }
}
