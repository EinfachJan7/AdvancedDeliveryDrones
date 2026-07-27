package de.cb.drones.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldGuardHookImpl implements IWorldGuardHook {
    public static StateFlag DRONE_START;
    public static StateFlag DRONE_LAND;
    public static StateFlag DRONE_RECEIVE;
    public static StateFlag DRONE_SOCKET_PLACE;
    public static StateFlag DRONE_SOCKET_USE;

    @Override
    public void onLoad() {
        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            
            DRONE_START = registerFlag(registry, "drone-start", true);
            DRONE_LAND = registerFlag(registry, "drone-land", true);
            DRONE_RECEIVE = registerFlag(registry, "drone-receive", true);
            DRONE_SOCKET_PLACE = registerFlag(registry, "drone-socket-place", true);
            DRONE_SOCKET_USE = registerFlag(registry, "drone-socket-use", true);
        } catch (NoClassDefFoundError e) {
            // Ignored, handled by caller
        }
    }

    private StateFlag registerFlag(FlagRegistry registry, String name, boolean defaultState) {
        try {
            StateFlag flag = new StateFlag(name, defaultState);
            registry.register(flag);
            return flag;
        } catch (Exception e) {
            if (e.getClass().getSimpleName().equals("FlagConflictException")) {
                com.sk89q.worldguard.protection.flags.Flag<?> existing = registry.get(name);
                if (existing instanceof StateFlag) {
                    return (StateFlag) existing;
                }
            }
        }
        return null;
    }

    private boolean testState(Location location, Player player, StateFlag flag) {
        if (flag == null) return true;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldguard.LocalPlayer localPlayer = player != null ? WorldGuardPlugin.inst().wrapPlayer(player) : null;
            return query.testState(BukkitAdapter.adapt(location), localPlayer, flag);
        } catch (NoClassDefFoundError | Exception e) {
            return true;
        }
    }

    @Override
    public boolean canStart(Location location, Player player) {
        return testState(location, player, DRONE_START);
    }

    @Override
    public boolean canLand(Location location, Player player) {
        return testState(location, player, DRONE_LAND);
    }

    @Override
    public boolean canReceive(Location location, Player player) {
        return testState(location, player, DRONE_RECEIVE);
    }

    @Override
    public boolean canPlaceSocket(Location location, Player player) {
        return testState(location, player, DRONE_SOCKET_PLACE);
    }

    @Override
    public boolean canUseSocket(Location location, Player player) {
        return testState(location, player, DRONE_SOCKET_USE);
    }
}
