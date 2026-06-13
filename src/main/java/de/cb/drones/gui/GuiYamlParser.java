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
        String rawMaterial = cfg.getString(itemSection + ".material", defaultMaterial.name());
        
        String customModelProvider = null;
        String customModelId = null;
        
        if (rawMaterial != null && (rawMaterial.contains(":") || rawMaterial.contains("-"))) {
            String lower = rawMaterial.toLowerCase();
            if (lower.startsWith("nexo:") || lower.startsWith("nexo-")) {
                customModelProvider = "NEXO";
                customModelId = rawMaterial.substring(5);
                rawMaterial = "STONE";
            } else if (lower.startsWith("oraxen:") || lower.startsWith("oraxen-")) {
                customModelProvider = "ORAXEN";
                customModelId = rawMaterial.substring(7);
                rawMaterial = "STONE";
            } else if (lower.startsWith("itemsadder:") || lower.startsWith("itemsadder-") || lower.startsWith("ia:") || lower.startsWith("ia-")) {
                customModelProvider = "ITEMSADDER";
                int prefixLen = lower.startsWith("ia") ? 3 : 11;
                customModelId = rawMaterial.substring(prefixLen);
                rawMaterial = "STONE";
            }
        }
        
        Material material = parseMaterial(rawMaterial, defaultMaterial);
        String name = cfg.getString(itemSection + ".name", fallback != null ? fallback.name() : "<white>Item");
        List<String> lore = cfg.getStringList(itemSection + ".lore");
        if (lore.isEmpty() && fallback != null) {
            lore = fallback.lore();
        }
        String headTexture = parseHeadTexture(cfg, itemSection, material);
        boolean enchanted = cfg.getBoolean(itemSection + ".enchanted", false);
        
        if (cfg.contains(itemSection + ".custom-model-provider")) {
            customModelProvider = cfg.getString(itemSection + ".custom-model-provider", "NONE");
            customModelId = cfg.getString(itemSection + ".custom-model-id");
        } else if (customModelProvider == null && fallback != null) {
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
        String customModelProvider = null;
        String customModelId = null;

        String localMaterial = cfg.getString(localFillSection + ".material");
        if (localMaterial == null || localMaterial.isBlank()) {
            localMaterial = cfg.getString("global.fill-item.material");
        }
        
        if (localMaterial != null && !localMaterial.isBlank()) {
            String lower = localMaterial.toLowerCase();
            if (lower.startsWith("nexo:") || lower.startsWith("nexo-")) {
                customModelProvider = "NEXO";
                customModelId = localMaterial.substring(5);
                fillMaterial = Material.GRAY_STAINED_GLASS_PANE;
            } else if (lower.startsWith("oraxen:") || lower.startsWith("oraxen-")) {
                customModelProvider = "ORAXEN";
                customModelId = localMaterial.substring(7);
                fillMaterial = Material.GRAY_STAINED_GLASS_PANE;
            } else if (lower.startsWith("itemsadder:") || lower.startsWith("itemsadder-") || lower.startsWith("ia:") || lower.startsWith("ia-")) {
                customModelProvider = "ITEMSADDER";
                int prefixLen = lower.startsWith("ia") ? 3 : 11;
                customModelId = localMaterial.substring(prefixLen);
                fillMaterial = Material.GRAY_STAINED_GLASS_PANE;
            } else {
                fillMaterial = parseMaterial(localMaterial, fillMaterial);
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

        String cfgCustomModelProvider = cfg.getString(localFillSection + ".custom-model-provider");
        String cfgCustomModelId = cfg.getString(localFillSection + ".custom-model-id");
        if (cfgCustomModelProvider == null) {
            cfgCustomModelProvider = cfg.getString("global.fill-item.custom-model-provider");
            cfgCustomModelId = cfg.getString("global.fill-item.custom-model-id");
        }
        
        if (cfgCustomModelProvider != null) {
            customModelProvider = cfgCustomModelProvider;
            customModelId = cfgCustomModelId;
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
