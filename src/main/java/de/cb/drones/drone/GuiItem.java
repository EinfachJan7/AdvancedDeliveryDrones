package de.cb.drones.drone;

import org.bukkit.Material;
import java.util.List;

public record GuiItem(
        int position,
        Material material,
        String name,
        List<String> lore,
        String headTexture,
        boolean enchanted,
        String customModelProvider,
        String customModelId
) {
    public GuiItem(int position, Material material, String name, List<String> lore) {
        this(position, material, name, lore, null, false, null, null);
    }

    public GuiItem(int position, Material material, String name, List<String> lore, String headTexture) {
        this(position, material, name, lore, headTexture, false, null, null);
    }

    public GuiItem(int position, Material material, String name, List<String> lore, String headTexture, boolean enchanted) {
        this(position, material, name, lore, headTexture, enchanted, null, null);
    }

    public GuiItem withPosition(int newPosition) {
        return new GuiItem(newPosition, material, name, lore, headTexture, enchanted, customModelProvider, customModelId);
    }

    public GuiItem mergeOverlay(GuiItem overlay) {
        if (overlay == null) {
            return this;
        }
        return new GuiItem(
                position,
                overlay.material() != null ? overlay.material() : material,
                overlay.name() != null ? overlay.name() : name,
                overlay.lore() != null && !overlay.lore().isEmpty() ? overlay.lore() : lore,
                overlay.headTexture() != null ? overlay.headTexture() : headTexture,
                overlay.enchanted() || enchanted,
                overlay.customModelProvider() != null ? overlay.customModelProvider() : customModelProvider,
                overlay.customModelId() != null ? overlay.customModelId() : customModelId
        );
    }
}
