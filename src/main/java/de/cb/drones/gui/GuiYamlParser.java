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
        boolean enchanted = cfg.getBoolean(itemSection + ".enchanted", false);
        
        String customModelProvider = null;
        String customModelId = null;
        if (cfg.contains(itemSection + ".custom-model-provider")) {
            customModelProvider = cfg.getString(itemSection + ".custom-model-provider", "NONE");
            customModelId = cfg.getString(itemSection + ".custom-model-id");
        } else if (fallback != null) {
            customModelProvider = fallback.customModelProvider();
            customModelId = fallback.customModelId();
        }

        return new GuiItem(position, material, name, lore, headTexture, enchanted, customModelProvider, customModelId);
    }

    public static GuiItem parseComposeHubVariant(FileConfiguration cfg, String variantSection, GuiItem base) {
        if (base == null || !cfg.contains(variantSection)) {
            return null;
        }
        GuiItem parsed = parseItem(cfg, variantSection, base);
        return parsed.withPosition(base.position());
    }

    public static GuiItem parseFillItem(FileConfiguration cfg, String localFillSection) {
        Material fillMaterial = Material.GRAY_STAINED_GLASS_PANE;
        String fillName = " ";

        String localMaterial = cfg.getString(localFillSection + ".material");
        if (localMaterial != null && !localMaterial.isBlank()) {
            fillMaterial = parseMaterial(localMaterial, fillMaterial);
        } else {
            String globalMaterial = cfg.getString("global.fill-item.material");
            if (globalMaterial != null && !globalMaterial.isBlank()) {
                fillMaterial = parseMaterial(globalMaterial, fillMaterial);
            }
        }

        String localName = cfg.getString(localFillSection + ".name");
        String globalName = cfg.getString("global.fill-item.name");
        if (localName != null) {
            fillName = localName;
        } else if (globalName != null) {
            fillName = globalName;
        }

        String headTexture = parseHeadTexture(cfg, localFillSection, fillMaterial);
        if (headTexture == null) {
            headTexture = parseHeadTexture(cfg, "global.fill-item", fillMaterial);
        }

        String customModelProvider = cfg.getString(localFillSection + ".custom-model-provider");
        String customModelId = cfg.getString(localFillSection + ".custom-model-id");
        if (customModelProvider == null) {
            customModelProvider = cfg.getString("global.fill-item.custom-model-provider");
            customModelId = cfg.getString("global.fill-item.custom-model-id");
        }

        return new GuiItem(-1, fillMaterial, fillName, List.of(), headTexture, false, customModelProvider, customModelId);
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
        Material parsed = Material.matchMaterial(value.trim(), false);
        if (parsed == null) {
            parsed = Material.matchMaterial(value.trim(), true);
        }
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

    public static int normalizeInventorySize(int requested) {
        return GuiItemStacks.normalizeInventorySize(requested);
    }
}
