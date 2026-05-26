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
        String[] languages = {"de_DE", "en_EN", "es_ES", "fr_FR", "ru_RU", "zh_CN"};
        for (String lang : languages) {
            String path = "languages/" + lang + ".yml";
            File file = new File(folder, lang + ".yml");
            if (!file.exists()) {
                try {
                    plugin.saveResource(path, false);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not save " + path + " from JAR: " + e.getMessage());
                }
            } else {
                ConfigUpdater.update(plugin, path);
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
