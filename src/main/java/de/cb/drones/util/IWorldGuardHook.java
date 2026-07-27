package de.cb.drones.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface IWorldGuardHook {
    void onLoad();
    boolean canStart(Location location, Player player);
    boolean canLand(Location location, Player player);
    boolean canReceive(Location location, Player player);
    boolean canPlaceSocket(Location location, Player player);
    boolean canUseSocket(Location location, Player player);
}
