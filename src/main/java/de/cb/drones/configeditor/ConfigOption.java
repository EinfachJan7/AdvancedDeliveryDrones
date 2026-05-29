package de.cb.drones.configeditor;

import org.bukkit.Material;

import java.util.List;

public record ConfigOption(
        String id,
        String configPath,
        ConfigOptionType type,
        String category,
        Material icon,
        String name,
        String description,
        List<String> enumValues
) {
    public ConfigOption(String id, String configPath, ConfigOptionType type, String category, Material icon, String name, String description) {
        this(id, configPath, type, category, icon, name, description, List.of());
    }
}
