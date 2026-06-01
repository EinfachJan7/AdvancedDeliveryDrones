package de.cb.drones.drone;

import org.bukkit.Material;
import java.util.List;

public record GuiItem(
        int position,
        Material material,
        String name,
        List<String> lore,
        String headTexture,
        boolean enchanted
) {
    public GuiItem(int position, Material material, String name, List<String> lore) {
        this(position, material, name, lore, null, false);
    }

    public GuiItem(int position, Material material, String name, List<String> lore, String headTexture) {
        this(position, material, name, lore, headTexture, false);
    }

    public GuiItem withPosition(int newPosition) {
        return new GuiItem(newPosition, material, name, lore, headTexture, enchanted);
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
                overlay.enchanted() || enchanted
        );
    }
}
