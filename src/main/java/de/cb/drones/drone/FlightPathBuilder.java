package de.cb.drones.drone;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Builds a {@link FlightPath} once from start/target. Sampling follows the same geometry as legacy cruise flight.
 */
final class FlightPathBuilder {
    private static final double SAMPLE_SPACING = 2.0D;
    private static final int MAX_SAMPLES = 256;

    private FlightPathBuilder() {
    }

    static FlightPath build(Location start, Location target) {
        if (start.getWorld() == null || target.getWorld() == null) {
            return emptyPath(start);
        }

        PathGeometry geometry = PathGeometry.from(start, target);
        if (geometry.totalDistance <= 0.001D) {
            Location end = geometry.sample(0.0D);
            return new FlightPath(List.of(end.clone()), new double[] {0.0D}, 0.0D);
        }

        int sampleCount = Math.min(
                MAX_SAMPLES,
                Math.max(2, (int) Math.ceil(geometry.totalDistance / SAMPLE_SPACING) + 1));
        double step = geometry.totalDistance / (sampleCount - 1);

        List<Location> waypoints = new ArrayList<>(sampleCount);
        double[] cumulativeDistance = new double[sampleCount];

        for (int i = 0; i < sampleCount; i++) {
            double traveled = i == sampleCount - 1 ? geometry.totalDistance : step * i;
            cumulativeDistance[i] = traveled;
            waypoints.add(geometry.sample(traveled));
        }

        return new FlightPath(waypoints, cumulativeDistance, geometry.totalDistance);
    }

    private static FlightPath emptyPath(Location fallback) {
        Location point = fallback != null ? fallback.clone() : new Location(null, 0, 0, 0);
        return new FlightPath(List.of(point), new double[] {0.0D}, 0.0D);
    }

    private static final class PathGeometry {
        private final Location start;
        private final Location target;
        private final float targetYaw;
        private final float targetPitch;
        private final boolean crossDimension;
        private final double totalDistance;

        private final double crossDimensionH1;
        private final double crossDimensionH2;
        private final double crossDimensionAscentHeight;
        private final double crossDimensionHorizontalDistance1;
        private final double crossDimensionHorizontalDistance2;
        private final double crossDimensionDescentHeight;
        private final double crossDimensionMidpointX;
        private final double crossDimensionMidpointZ;
        private final double crossDimensionTargetMidpointX;
        private final double crossDimensionTargetMidpointZ;

        private final double pathDeltaX;
        private final double pathDeltaY;
        private final double pathDeltaZ;

        private PathGeometry(
                Location start,
                Location target,
                boolean crossDimension,
                double totalDistance,
                double crossDimensionH1,
                double crossDimensionH2,
                double crossDimensionAscentHeight,
                double crossDimensionHorizontalDistance1,
                double crossDimensionHorizontalDistance2,
                double crossDimensionDescentHeight,
                double crossDimensionMidpointX,
                double crossDimensionMidpointZ,
                double crossDimensionTargetMidpointX,
                double crossDimensionTargetMidpointZ,
                double pathDeltaX,
                double pathDeltaY,
                double pathDeltaZ
        ) {
            this.start = start;
            this.target = target;
            this.targetYaw = target.getYaw();
            this.targetPitch = target.getPitch();
            this.crossDimension = crossDimension;
            this.totalDistance = totalDistance;
            this.crossDimensionH1 = crossDimensionH1;
            this.crossDimensionH2 = crossDimensionH2;
            this.crossDimensionAscentHeight = crossDimensionAscentHeight;
            this.crossDimensionHorizontalDistance1 = crossDimensionHorizontalDistance1;
            this.crossDimensionHorizontalDistance2 = crossDimensionHorizontalDistance2;
            this.crossDimensionDescentHeight = crossDimensionDescentHeight;
            this.crossDimensionMidpointX = crossDimensionMidpointX;
            this.crossDimensionMidpointZ = crossDimensionMidpointZ;
            this.crossDimensionTargetMidpointX = crossDimensionTargetMidpointX;
            this.crossDimensionTargetMidpointZ = crossDimensionTargetMidpointZ;
            this.pathDeltaX = pathDeltaX;
            this.pathDeltaY = pathDeltaY;
            this.pathDeltaZ = pathDeltaZ;
        }

        static PathGeometry from(Location start, Location target) {
            boolean crossDimension = !start.getWorld().equals(target.getWorld());
            if (crossDimension) {
                return buildCrossDimension(start, target);
            }
            double targetY = target.getY() + 0.1;
            double dx = target.getX() - start.getX();
            double dy = targetY - start.getY();
            double dz = target.getZ() - start.getZ();
            double total = Math.sqrt(dx * dx + dy * dy + dz * dz);
            return new PathGeometry(
                    start, target, false, total,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    dx, dy, dz
            );
        }

        private static PathGeometry buildCrossDimension(Location start, Location target) {
            String sourceName = start.getWorld().getName().toLowerCase();
            String targetName = target.getWorld().getName().toLowerCase();

            double h1 = sourceName.contains("nether") ? 120.0 : 280.0;
            double h2 = targetName.contains("nether") ? 120.0 : 280.0;
            double ascentHeight = Math.abs(h1 - start.getY());

            double startScale = coordinateScale(start.getWorld());
            double targetScale = coordinateScale(target.getWorld());

            double startXProj = start.getX() * startScale;
            double startZProj = start.getZ() * startScale;
            double targetXProj = target.getX() * targetScale;
            double targetZProj = target.getZ() * targetScale;

            double projMidpointX = (startXProj + targetXProj) / 2.0;
            double projMidpointZ = (startZProj + targetZProj) / 2.0;

            double midpointX = projMidpointX / startScale;
            double midpointZ = projMidpointZ / startScale;
            double targetMidpointX = projMidpointX / targetScale;
            double targetMidpointZ = projMidpointZ / targetScale;

            double dx1 = midpointX - start.getX();
            double dz1 = midpointZ - start.getZ();
            double horizontal1 = Math.sqrt(dx1 * dx1 + dz1 * dz1);

            double dx2 = target.getX() - targetMidpointX;
            double dz2 = target.getZ() - targetMidpointZ;
            double horizontal2 = Math.sqrt(dx2 * dx2 + dz2 * dz2);

            double descentHeight = Math.abs(h2 - (target.getY() + 0.1));
            double total = ascentHeight + horizontal1 + horizontal2 + descentHeight;

            return new PathGeometry(
                    start, target, true, total,
                    h1, h2, ascentHeight, horizontal1, horizontal2, descentHeight,
                    midpointX, midpointZ, targetMidpointX, targetMidpointZ,
                    0, 0, 0
            );
        }

        Location sample(double traveled) {
            double d = Math.max(0.0D, Math.min(totalDistance, traveled));
            if (crossDimension) {
                return sampleCrossDimension(d);
            }
            double factor = totalDistance <= 0.001D ? 1.0D : d / totalDistance;
            return new Location(
                    start.getWorld(),
                    start.getX() + pathDeltaX * factor,
                    start.getY() + pathDeltaY * factor,
                    start.getZ() + pathDeltaZ * factor,
                    targetYaw,
                    targetPitch
            );
        }

        private Location sampleCrossDimension(double d) {
            double d1 = crossDimensionAscentHeight;
            double d2 = d1 + crossDimensionHorizontalDistance1;
            double d3 = d2 + crossDimensionHorizontalDistance2;
            double targetY = target.getY() + 0.1;

            if (d < d1) {
                double fraction = d1 > 0.001 ? d / d1 : 1.0;
                double y = start.getY() + fraction * (crossDimensionH1 - start.getY());
                return new Location(
                        start.getWorld(),
                        start.getX(),
                        y,
                        start.getZ(),
                        targetYaw,
                        targetPitch
                );
            }
            if (d < d2) {
                double fraction = crossDimensionHorizontalDistance1 > 0.001
                        ? (d - d1) / crossDimensionHorizontalDistance1 : 1.0;
                double x = start.getX() + fraction * (crossDimensionMidpointX - start.getX());
                double z = start.getZ() + fraction * (crossDimensionMidpointZ - start.getZ());
                return new Location(
                        start.getWorld(),
                        x,
                        crossDimensionH1,
                        z,
                        targetYaw,
                        targetPitch
                );
            }
            if (d < d3) {
                double fraction = crossDimensionHorizontalDistance2 > 0.001
                        ? (d - d2) / crossDimensionHorizontalDistance2 : 1.0;
                double x = crossDimensionTargetMidpointX
                        + fraction * (target.getX() - crossDimensionTargetMidpointX);
                double z = crossDimensionTargetMidpointZ
                        + fraction * (target.getZ() - crossDimensionTargetMidpointZ);
                return new Location(
                        target.getWorld(),
                        x,
                        crossDimensionH2,
                        z,
                        targetYaw,
                        targetPitch
                );
            }
            double fraction = crossDimensionDescentHeight > 0.001 ? (d - d3) / crossDimensionDescentHeight : 1.0;
            double y = crossDimensionH2 - fraction * (crossDimensionH2 - targetY);
            return new Location(
                    target.getWorld(),
                    target.getX(),
                    y,
                    target.getZ(),
                    targetYaw,
                    targetPitch
            );
        }
    }

    private static double coordinateScale(World world) {
        if (world == null) {
            return 1.0;
        }
        String name = world.getName().toLowerCase();
        if (name.contains("nether")) {
            return 8.0;
        }
        return 1.0;
    }
}
