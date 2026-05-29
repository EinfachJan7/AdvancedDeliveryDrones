package de.cb.drones.drone;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Precomputed flight polyline. Position lookup is O(log n) with no world access.
 */
public final class FlightPath {
    private final List<Location> waypoints;
    private final double[] cumulativeDistance;
    private final double totalDistance;

    FlightPath(List<Location> waypoints, double[] cumulativeDistance, double totalDistance) {
        this.waypoints = List.copyOf(waypoints);
        this.cumulativeDistance = cumulativeDistance;
        this.totalDistance = totalDistance;
    }

    public double totalDistance() {
        return totalDistance;
    }

    public Location positionAt(double traveled) {
        if (waypoints.isEmpty()) {
            return null;
        }
        if (traveled <= 0.0D) {
            return waypoints.getFirst().clone();
        }
        if (traveled >= totalDistance) {
            return waypoints.getLast().clone();
        }

        int index = findSegmentIndex(traveled);
        Location from = waypoints.get(index);
        Location to = waypoints.get(index + 1);
        double segStart = cumulativeDistance[index];
        double segLen = cumulativeDistance[index + 1] - segStart;
        double t = segLen > 0.0001D ? (traveled - segStart) / segLen : 1.0D;

        World world = from.getWorld();
        if (world == null || to.getWorld() == null || !world.equals(to.getWorld())) {
            return to.clone();
        }

        double x = from.getX() + (to.getX() - from.getX()) * t;
        double y = from.getY() + (to.getY() - from.getY()) * t;
        double z = from.getZ() + (to.getZ() - from.getZ()) * t;
        float yaw = (float) (from.getYaw() + (to.getYaw() - from.getYaw()) * t);
        float pitch = (float) (from.getPitch() + (to.getPitch() - from.getPitch()) * t);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private int findSegmentIndex(double traveled) {
        int low = 0;
        int high = cumulativeDistance.length - 2;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (cumulativeDistance[mid] <= traveled) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }
}
