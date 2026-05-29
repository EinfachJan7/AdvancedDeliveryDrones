package de.cb.drones.placeholder;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import org.bukkit.Bukkit;

public final class PlaceholderHook {

    private PlaceholderHook() {
    }

    public static void register(AdvancedDeliveryDronesPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        DeliveryDronesExpansion expansion = new DeliveryDronesExpansion(plugin);
        if (expansion.register()) {
            plugin.getLogger().info("PlaceholderAPI hooked (%deliverydrones_<...>%)");
        }
    }
}
