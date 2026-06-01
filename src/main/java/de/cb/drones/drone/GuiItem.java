package de.cb.drones.drone;

import org.bukkit.Material;
import java.util.List;

public record GuiItem(
        int position,
        Material material,
        String name,
        List<String> lore,
        String headTexture
) {
    public GuiItem(int position, Material material, String name, List<String> lore) {
        this(position, material, name, lore, null);
    }
}
