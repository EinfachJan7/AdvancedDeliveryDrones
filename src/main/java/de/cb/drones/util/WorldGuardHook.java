package de.cb.drones.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

public class WorldGuardHook {
    public static StateFlag DRONE_START;
    public static StateFlag DRONE_LAND;
    public static StateFlag DRONE_RECEIVE;
    public static StateFlag DRONE_SOCKET_PLACE;
    public static StateFlag DRONE_SOCKET_USE;
    private static boolean enabled = false;

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
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            
            DRONE_START = registerFlag(registry, "drone-start", true);
            DRONE_LAND = registerFlag(registry, "drone-land", true);
            DRONE_RECEIVE = registerFlag(registry, "drone-receive", true);
            DRONE_SOCKET_PLACE = registerFlag(registry, "drone-socket-place", true);
            DRONE_SOCKET_USE = registerFlag(registry, "drone-socket-use", true);
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            // WorldGuard not found
            enabled = false;
        }
    }

    private static StateFlag registerFlag(FlagRegistry registry, String name, boolean defaultState) {
        try {
            StateFlag flag = new StateFlag(name, defaultState);
            registry.register(flag);
            return flag;
        } catch (FlagConflictException e) {
            com.sk89q.worldguard.protection.flags.Flag<?> existing = registry.get(name);
            if (existing instanceof StateFlag) {
                return (StateFlag) existing;
            }
        }
        return null;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static boolean testState(Location location, Player player, StateFlag flag) {
        if (!enabled || flag == null) return true;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldguard.LocalPlayer localPlayer = player != null ? WorldGuardPlugin.inst().wrapPlayer(player) : null;
            return query.testState(BukkitAdapter.adapt(location), localPlayer, flag);
        } catch (NoClassDefFoundError | Exception e) {
            return true;
        }
    }

    public static boolean canStart(Location location, Player player) {
        return testState(location, player, DRONE_START);
    }

    public static boolean canLand(Location location, Player player) {
        return testState(location, player, DRONE_LAND);
    }

    public static boolean canReceive(Location location, Player player) {
        return testState(location, player, DRONE_RECEIVE);
    }

    public static boolean canPlaceSocket(Location location, Player player) {
        return testState(location, player, DRONE_SOCKET_PLACE);
    }

    public static boolean canUseSocket(Location location, Player player) {
        return testState(location, player, DRONE_SOCKET_USE);
    }
}
