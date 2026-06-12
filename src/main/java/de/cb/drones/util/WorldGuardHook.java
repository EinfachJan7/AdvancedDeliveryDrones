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
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class WorldGuardHook {
    public static StateFlag DRONE_START;
    public static StateFlag DRONE_LAND;
    private static boolean enabled = false;

    public static void onLoad() {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            
            try {
                StateFlag startFlag = new StateFlag("drone-start", true);
                registry.register(startFlag);
                DRONE_START = startFlag;
            } catch (FlagConflictException e) {
                com.sk89q.worldguard.protection.flags.Flag<?> existing = registry.get("drone-start");
                if (existing instanceof StateFlag) {
                    DRONE_START = (StateFlag) existing;
                }
            }

            try {
                StateFlag landFlag = new StateFlag("drone-land", true);
                registry.register(landFlag);
                DRONE_LAND = landFlag;
            } catch (FlagConflictException e) {
                com.sk89q.worldguard.protection.flags.Flag<?> existing = registry.get("drone-land");
                if (existing instanceof StateFlag) {
                    DRONE_LAND = (StateFlag) existing;
                }
            }
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            // WorldGuard not found
        }
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean canStart(Location location, Player player) {
        if (!enabled || DRONE_START == null) return true;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldguard.LocalPlayer localPlayer = player != null ? WorldGuardPlugin.inst().wrapPlayer(player) : null;
            return query.testState(BukkitAdapter.adapt(location), localPlayer, DRONE_START);
        } catch (NoClassDefFoundError | Exception e) {
            return true;
        }
    }

    public static boolean canLand(Location location, Player player) {
        if (!enabled || DRONE_LAND == null) return true;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldguard.LocalPlayer localPlayer = player != null ? WorldGuardPlugin.inst().wrapPlayer(player) : null;
            return query.testState(BukkitAdapter.adapt(location), localPlayer, DRONE_LAND);
        } catch (NoClassDefFoundError | Exception e) {
            return true;
        }
    }
}
