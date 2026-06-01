package de.cb.drones.gui;

import de.cb.drones.drone.GuiItem;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public final class GuiYamlParser {

    private GuiYamlParser() {
    }

    public static GuiItem parseItem(FileConfiguration cfg, String itemSection, GuiItem fallback) {
        int position = cfg.getInt(itemSection + ".position", fallback != null ? fallback.position() : 0);
        Material defaultMaterial = fallback != null ? fallback.material() : Material.STONE;
        Material material = parseMaterial(cfg.getString(itemSection + ".material", defaultMaterial.name()), defaultMaterial);
        String name = cfg.getString(itemSection + ".name", fallback != null ? fallback.name() : "<white>Item");
        List<String> lore = cfg.getStringList(itemSection + ".lore");
        if (lore.isEmpty() && fallback != null) {
            lore = fallback.lore();
        }
        String headTexture = parseHeadTexture(cfg, itemSection, material);
        return new GuiItem(position, material, name, lore, headTexture);
    }

    public static GuiItem parseFillItem(FileConfiguration cfg, String localFillSection) {
        boolean hasLocalFillItem = cfg.contains(localFillSection);
        boolean hasGlobalFillItem = cfg.contains("global.fill-item");

        Material fillMaterial = Material.GRAY_STAINED_GLASS_PANE;
        String fillName = " ";

        if (hasLocalFillItem && cfg.contains(localFillSection + ".material")) {
            fillMaterial = parseMaterial(cfg.getString(localFillSection + ".material"), fillMaterial);
        } else if (hasGlobalFillItem && cfg.contains("global.fill-item.material")) {
            fillMaterial = parseMaterial(cfg.getString("global.fill-item.material"), fillMaterial);
        }

        if (hasLocalFillItem && cfg.contains(localFillSection + ".name")) {
            fillName = cfg.getString(localFillSection + ".name");
        } else if (hasGlobalFillItem && cfg.contains("global.fill-item.name")) {
            fillName = cfg.getString("global.fill-item.name");
        }

        String headTexture = parseHeadTexture(cfg, localFillSection, fillMaterial);
        if (headTexture == null && hasGlobalFillItem) {
            headTexture = parseHeadTexture(cfg, "global.fill-item", fillMaterial);
        }

        return new GuiItem(-1, fillMaterial, fillName, List.of(), headTexture);
    }

    public static String parseHeadTexture(FileConfiguration cfg, String section, Material material) {
        if (material != Material.PLAYER_HEAD) {
            return null;
        }
        return firstNonBlank(
                cfg.getString(section + ".value"),
                cfg.getString(section + ".head-texture"),
                cfg.getString(section + ".texture")
        );
    }

    public static Material parseMaterial(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(value.trim(), true);
        return parsed == null ? fallback : parsed;
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
