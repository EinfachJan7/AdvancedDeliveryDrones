package de.cb.drones.util;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

public class WorldGuardHook {
    private static boolean enabled = false;
    private static IWorldGuardHook impl;

    public static void onLoad(File dataFolder) {
        // Read config directly to check if hook is enabled
        File configFile = new File(dataFolder, "config.yml");
        if (configFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            enabled = config.getBoolean("hooks.worldguard", true);
        } else {
            enabled = true; // Default if not found
        }

        if (!enabled) {
            return; // Don't register flags if disabled
        }

        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            impl = (IWorldGuardHook) Class.forName("de.cb.drones.util.WorldGuardHookImpl").getDeclaredConstructor().newInstance();
            impl.onLoad();
        } catch (Throwable e) {
            // WorldGuard not found or could not be loaded
            enabled = false;
            impl = null;
        }
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean canStart(Location location, Player player) {
        if (!enabled || impl == null) return true;
        return impl.canStart(location, player);
    }

    public static boolean canLand(Location location, Player player) {
        if (!enabled || impl == null) return true;
        return impl.canLand(location, player);
    }

    public static boolean canReceive(Location location, Player player) {
        if (!enabled || impl == null) return true;
        return impl.canReceive(location, player);
    }

    public static boolean canPlaceSocket(Location location, Player player) {
        if (!enabled || impl == null) return true;
        return impl.canPlaceSocket(location, player);
    }

    public static boolean canUseSocket(Location location, Player player) {
        if (!enabled || impl == null) return true;
        return impl.canUseSocket(location, player);
    }
}
