package de.cb.drones.gui;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record PlayerHeadItemConfig(String nameFormat, List<String> lore, String texture) {

    public static PlayerHeadItemConfig parse(
            FileConfiguration cfg,
            String section,
            String defaultNameFormat,
            List<String> defaultLore
    ) {
        String nameFormat = cfg.getString(section + ".name-format", defaultNameFormat);
        List<String> lore = cfg.getStringList(section + ".lore");
        if (lore.isEmpty()) {
            lore = defaultLore;
        }
        String texture = GuiYamlParser.parseHeadTexture(cfg, section, Material.PLAYER_HEAD);
        return new PlayerHeadItemConfig(nameFormat, lore, texture);
    }
}
