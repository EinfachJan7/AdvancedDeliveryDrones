package de.cb.drones.drone;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import de.cb.drones.socket.DeliverySocket;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.HeightMap;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class DeliveryDrone {
    private final UUID droneId;
    private final UUID senderId;
    private final UUID receiverId;
    private final String receiverName;
    private final Location fixedTarget;
    private final Location startLocation;
    private long flightStartTick;
    /** Wall-clock flight start for airborne follow window (not reset by mid-flight follow). */
    private long deliveryFlightStartTick = -1L;
    private final Inventory inventory;
    private final List<EntityType> attachedAnimalTypes;
    private final boolean animalsOnlyDelivery;
    private final boolean forceTargetChunkLoad;
    private final boolean exactSocketTarget;
    private final String socketName;
    private ArmorStand stand;
    private UUID standId;
    private Location lastKnownLocation;
    private final Deque<Location> particleTrail = new LinkedList<>();
    private final List<UUID> spawnedTransportAnimalIds = new ArrayList<>();
    private ArmorStand hologramStand;
    private DroneManager droneManager;

    private DroneSettings settings;
    private boolean landed;
    private boolean landingNotified;
    private boolean targetChunkPreloaded;
    private boolean standParked;
    private Location pendingLanding;
    private Location cachedLandingSpot;
    private String cachedLandingSpotKey;
    private Location smoothLandingEnd;
    private Location landedLocation;
    private boolean openedByReceiver;
    private long lastInteractionTick;
    private BukkitTask ticker;
    private BukkitTask beaconTicker;
    /** Blacklisted container detected — do not scan again for this drone. */
    private boolean containerIntegrationAborted;
    private boolean containerTargetCached;
    private Location cachedContainerBlock;
    private BossBar bossBar;
    private boolean smoothLanding;
    private Location smoothLandingStart;
    private long smoothLandingStartTick;
    private final int smoothLandingDuration = 60; // 3 seconds at 20 ticks
    private boolean collectionAnimation;
    private Location collectionAnimationStart;
    private long collectionAnimationStartTick;
    private final int collectionAnimationDuration = 40; // 2 seconds at 20 ticks
    private long lastBossBarUpdate = 0L;
    private static final long BOSSBAR_UPDATE_INTERVAL = 20L;
    private int lastBossBarDistanceM = -1;
    private long lastBossBarEta = -1L;
    private long approachPhaseStartTick = -1L; // -1 means not in approach phase yet
    private double distanceAtApproachStart = -1.0;
    private boolean wasGlidingFollowed = false;
    private boolean wasAirborneFollowed = false;
    private boolean allowGlideFollow = false;
    /** One airborne follow + relocate cycle per delivery (like elytra glide follow). */
    private boolean allowAirborneFollow = true;
    
    // Performance caches
    private long lastTraveledDistanceTick = -1L;
    private double cachedTraveledDistance = 0.0;
    private long lastParticleUpdateTick = -1L;
    private static final long PARTICLE_UPDATE_INTERVAL = 8L;
    private long lastHologramUpdateTick = -1L;
    private static final long HOLOGRAM_UPDATE_INTERVAL = 60L; // Hologram every 3 seconds (60 ticks)
    private long lastExpectedLocationTick = -1L;
    private Location cachedExpectedLocation = null;
    private long lastMovementTick = -1L;
    private static final long MOVEMENT_INTERVAL = 1L;
    private static final double MOVEMENT_SNAP_DISTANCE_SQ = 64.0D;
    private static final double MOVEMENT_EPSILON_SQ = 0.0004D;
    private final Location movementScratch = new Location(null, 0, 0, 0);
    private long lastChunkPreloadCheckTick = -1L;
    private static final long CHUNK_PRELOAD_CHECK_INTERVAL = 20L;
    private double deliveryRadiusSq;
    private long lastSoundTick = -1L;
    private static final long SOUND_INTERVAL = 10L; // Sound every 10 ticks to reduce audio overhead
    
    // Precomputed flight path (rebuilt when cruise flight starts or startLocation changes)
    private double pathDeltaX;
    private double pathDeltaY;
    private double pathDeltaZ;
    private double pathTotalDistance;
    private boolean pathComputed;
    
    // Cross-dimension pathing fields
    private boolean isCrossDimension;
    private double crossDimensionH1;
    private double crossDimensionH2;
    private double crossDimensionAscentHeight;
    private double crossDimensionHorizontalDistance;
    private double crossDimensionDescentHeight;
    private double crossDimensionMidpointX;
    private double crossDimensionMidpointZ;
    private double crossDimensionTargetMidpointX;
    private double crossDimensionTargetMidpointZ;
    private double crossDimensionHorizontalDistance1;
    private double crossDimensionHorizontalDistance2;

    // Socket pickup tracking
    private UUID socketPickupPlayerId;
    private String socketPickupSocketName;
    private boolean notificationsSent;
    
    public DeliveryDrone(
            UUID droneId,
            UUID senderId,
            UUID receiverId,
            String receiverName,
            Location fixedTarget,
            Inventory inventory,
            List<EntityType> attachedAnimalTypes,
            boolean animalsOnlyDelivery,
            boolean forceTargetChunkLoad,
            boolean exactSocketTarget,
            String socketName,
            DroneSettings settings,
            ArmorStand stand,
            long createdTick
    ) {
        this.droneId = droneId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.receiverName = receiverName;
        this.fixedTarget = fixedTarget;
        this.flightStartTick = createdTick;
        this.inventory = inventory;
        this.attachedAnimalTypes = attachedAnimalTypes == null ? List.of() : List.copyOf(attachedAnimalTypes);
        this.animalsOnlyDelivery = animalsOnlyDelivery;
        this.forceTargetChunkLoad = forceTargetChunkLoad;
        this.exactSocketTarget = exactSocketTarget;
        this.socketName = socketName;
        this.settings = settings;
        refreshDerivedSettings();
        this.stand = stand;
        if (stand != null) {
            this.standId = stand.getUniqueId();
            this.startLocation = stand.getLocation().clone();
            this.lastKnownLocation = stand.getLocation().clone();
        } else {
            this.standId = null;
            this.startLocation = fixedTarget.clone(); // Will be overwritten in fromPersistentData
            this.lastKnownLocation = fixedTarget.clone(); // Will be overwritten in fromPersistentData
        }
        this.lastInteractionTick = createdTick;
        Player receiver = Bukkit.getPlayer(receiverId);
        this.allowGlideFollow = receiver != null && receiver.isOnline() && receiver.isGliding();
    }

    public UUID droneId() {
        return droneId;
    }

    public UUID standId() {
        return standId;
    }

    public UUID receiverId() {
        return receiverId;
    }

    public UUID senderId() {
        return senderId;
    }

    public String receiverName() {
        return receiverName;
    }

    public String socketName() {
        return socketName;
    }

    public Inventory inventory() {
        return inventory;
    }

    public Location currentLocation() {
        if (stand != null && !stand.isDead()) {
            return stand.getLocation().clone();
        }
        return lastKnownLocation.clone();
    }

    public boolean isLanded() {
        return landed;
    }
    
    public boolean isFlying() {
        return !landed;
    }

    public boolean isExpired(long currentTick) {
        // Despawn timer only starts after landing
        if (!landed) {
            return false;
        }
        return currentTick - lastInteractionTick > settings.despawnTicks();
    }

    public long lastInteractionTick() {
        return lastInteractionTick;
    }

    public boolean wasOpenedByReceiver() {
        return openedByReceiver;
    }

    public boolean animalsOnlyDelivery() {
        return animalsOnlyDelivery;
    }

    public List<EntityType> attachedAnimalTypes() {
        return attachedAnimalTypes;
    }
    
    public Location startLocation() {
        return startLocation;
    }
    
    public List<UUID> getSpawnedTransportAnimalIds() {
        return new ArrayList<>(spawnedTransportAnimalIds);
    }

    public List<ItemStack> snapshotItems() {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() != Material.AIR) {
                items.add(stack.clone());
            }
        }
        return items;
    }

    public void clearItems() {
        inventory.clear();
    }

    public void markInteraction(long tick) {
        this.lastInteractionTick = tick;
    }

    public void markAsSocketPickup(UUID playerId, String socketName) {
        this.socketPickupPlayerId = playerId;
        this.socketPickupSocketName = socketName;
        this.notificationsSent = false;
    }

    public boolean areNotificationsSent() {
        return notificationsSent;
    }

    public void markNotificationsSent() {
        this.notificationsSent = true;
    }

    public boolean isSocketPickup() {
        return socketPickupPlayerId != null && socketPickupSocketName != null;
    }

    public UUID socketPickupPlayerId() {
        return socketPickupPlayerId;
    }

    public String socketPickupSocketName() {
        return socketPickupSocketName;
    }

    public void onReceiverOpened() {
        this.openedByReceiver = true;
    }

    public void attachLeashedAnimal() {
        if (stand == null || stand.isDead() || !landed) {
            return;
        }
        for (LivingEntity animal : attachedAnimals()) {
            if (animal.isDead()) {
                continue;
            }
            if (!animal.getWorld().equals(stand.getWorld())) {
                continue;
            }
            try {
                animal.setLeashHolder(stand);
            } catch (IllegalStateException ignored) {
                // Some entities can reject leash changes based on state.
            }
        }
    }

    public void releaseLeashedAnimal() {
        for (LivingEntity animal : attachedAnimals()) {
            if (animal.isDead()) {
                continue;
            }
            if (animal.isLeashed()) {
                Entity holder = animal.getLeashHolder();
                boolean isDroneHolder = stand != null && !stand.isDead() && holder != null && holder.getUniqueId().equals(stand.getUniqueId());
                if (isDroneHolder) {
                    animal.setLeashHolder(null);
                }
            }
            animal.setInvulnerable(false);
        }
    }

    public void applySettings(DroneSettings settings, DroneManager manager) {
        this.settings = settings;
        refreshDerivedSettings();
        if (stand != null && !stand.isDead()) {
            applyDroneHelmet(stand, settings);
        }
        if (landed) {
            if (stand != null && !stand.isDead()) {
                stand.setGlowing(settings.glowingEnabled());
                stand.setCustomNameVisible(settings.hologramEnabled());
            }
            if (settings.hologramEnabled()) {
                updateHologram(Bukkit.getCurrentTick(), manager);
            }
        }
    }

    public void startFlight(DroneManager manager) {
        this.droneManager = manager;

        if (settings.launchAnimationEnabled()) {
            startLaunchAnimation(manager);
        } else {
            startFlightInternal(manager);
        }
    }

    private void startLaunchAnimation(DroneManager manager) {
        int totalTicks = settings.launchAnimationSeconds() * 20;
        Location startAnimLocation = stand.getLocation().clone();
        float startYaw = stand.getYaw();

        class LaunchAnimationTask extends org.bukkit.scheduler.BukkitRunnable {
            int ticks = 0;

            @Override
            public void run() {
                if (stand == null || stand.isDead()) {
                    cancel();
                    return;
                }

                double progress = (double) ticks / totalTicks;

                double easedProgress = progress < 0.5
                        ? 4 * progress * progress * progress
                        : 1 - Math.pow(-2 * progress + 2, 3) / 2;

                double riseHeight = easedProgress * 1.5;

                Location newPos = startAnimLocation.clone();
                newPos.setY(startAnimLocation.getY() + riseHeight);

                double hoverOffset = Math.sin(progress * Math.PI * 1.5) * 0.05;
                newPos.setY(newPos.getY() + hoverOffset);

                float spinAngle = (float) (startYaw + (easedProgress * 360 * 1.5));
                newPos.setYaw(spinAngle);

                teleportStand(newPos);

                Location center = newPos.clone();

                double angle = ticks * 0.3;
                double radius = 2.0 * (1 - progress * 0.5);

                if (ticks % 2 == 0) {
                    for (int i = 0; i < 4; i++) {
                        double particleAngle = angle + (i * Math.PI / 2);
                        double x = center.getX() + Math.cos(particleAngle) * radius;
                        double z = center.getZ() + Math.sin(particleAngle) * radius;
                        double y = center.getY() + Math.sin(particleAngle * 1.5 + ticks * 0.1) * 0.2;
                        Location particleLoc = new Location(center.getWorld(), x, y, z);
                        center.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, particleLoc, 1, 0.02, 0.02, 0.02, 0.005);
                        if (ticks % 6 == 0) {
                            center.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, particleLoc, 1, 0.03, 0.03, 0.03, 0.005);
                        }
                    }
                }

                if (ticks % 6 == 0) {
                    double cloudAngle = Math.random() * Math.PI * 2;
                    double dist = Math.random() * 0.3;
                    Location below = center.clone().add(
                            Math.cos(cloudAngle) * dist,
                            -0.3 - (Math.random() * 0.2),
                            Math.sin(cloudAngle) * dist
                    );
                    center.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, below, 1, 0.03, 0.05, 0.03, 0.01);
                }

                if (ticks % 12 == 0) {
                    center.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, center.clone().add(0, 0.3, 0), 3, 0.3, 0.3, 0.3, 0.01);
                }

                if (ticks == 0) {
                    center.getWorld().playSound(center, settings.launchSound(), settings.launchSoundVolume() * 0.8f, 0.9f);
                }

                if (ticks % 15 == 0) {
                    center.getWorld().playSound(center, settings.flightSound(), 0.05f, 0.9f + (float) progress * 0.2f);
                }

                ticks++;

                if (ticks >= totalTicks) {
                    cancel();

                    center.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, center, 5, 0.4, 0.4, 0.4, 0.08);
                    center.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, center, 25, 0.4, 0.4, 0.4, 0.25);
                    center.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, center, 12, 0.5, 0.5, 0.5, 0.03);
                    center.getWorld().playSound(center, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);

                    startLocation.setX(center.getX());
                    startLocation.setY(center.getY());
                    startLocation.setZ(center.getZ());
                    startLocation.setYaw(center.getYaw());
                    startLocation.setPitch(center.getPitch());

                    startFlightInternal(manager);
                }
            }
        }

        new LaunchAnimationTask().runTaskTimer(manager.plugin(), 0L, 1L);
    }

    private boolean isStandAlive() {
        return stand != null && !stand.isDead();
    }

    private void enableStandPhysics() {
        if (isStandAlive()) {
            stand.setCanTick(true);
        }
    }

    private void stopStandMotion() {
        if (isStandAlive()) {
            stand.setVelocity(new Vector(0, 0, 0));
        }
    }

    private void disableStandPhysics() {
        stopStandMotion();
        if (isStandAlive()) {
            stand.setCanTick(false);
        }
    }

    /**
     * Moves the armor stand via velocity (blocks/tick delta). Teleports only for dimension changes or large corrections.
     */
    private void moveStandToward(Location target, Float yaw) {
        if (!isStandAlive() || target.getWorld() == null) {
            return;
        }

        Location current = stand.getLocation();
        World currentWorld = current.getWorld();
        if (currentWorld == null || !currentWorld.equals(target.getWorld())) {
            if (yaw != null) {
                target.setYaw(yaw);
            }
            teleportStand(target);
            return;
        }

        double dx = target.getX() - current.getX();
        double dy = target.getY() - current.getY();
        double dz = target.getZ() - current.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > MOVEMENT_SNAP_DISTANCE_SQ) {
            if (yaw != null) {
                target.setYaw(yaw);
            }
            teleportStand(target);
            return;
        }

        if (distSq > MOVEMENT_EPSILON_SQ) {
            stand.setVelocity(new Vector(dx, dy, dz));
        } else {
            stand.setVelocity(new Vector(0, 0, 0));
        }

        if (yaw != null && Math.abs(current.getYaw() - yaw) > 3.0f) {
            stand.setRotation(yaw, current.getPitch());
        }
    }

    private boolean canStartAirborneFollow(long nowTick) {
        if (!allowAirborneFollow) {
            return false;
        }
        int maxSeconds = settings.airborneFollowMaxSecondsAfterStart();
        if (maxSeconds <= 0 || deliveryFlightStartTick < 0L) {
            return true;
        }
        return nowTick - deliveryFlightStartTick < maxSeconds * 20L;
    }

    private void expireAirborneFollowWindow(long nowTick) {
        int maxSeconds = settings.airborneFollowMaxSecondsAfterStart();
        if (maxSeconds > 0 && deliveryFlightStartTick >= 0L
                && nowTick - deliveryFlightStartTick >= maxSeconds * 20L) {
            allowAirborneFollow = false;
        }
    }

    private boolean isInStartupPhase(long nowTick) {
        long startupTicks = settings.startupSeconds() * 20L;
        if (startupTicks <= 0L) {
            return false;
        }
        return nowTick - flightStartTick < startupTicks;
    }

    /**
     * Teleport during startup/landing (slow, precise); velocity during cruise for performance.
     */
    private void syncStandToExpected(Location expected, long nowTick, boolean forceTeleport) {
        if (!isStandAlive() || expected.getWorld() == null) {
            return;
        }
        if (forceTeleport || isInStartupPhase(nowTick)) {
            teleportStand(expected);
            return;
        }
        moveStandToward(expected, expected.getYaw());
    }

    private void startFlightInternal(DroneManager manager) {
        long nowTick = Bukkit.getCurrentTick();
        this.deliveryFlightStartTick = nowTick;
        this.flightStartTick = nowTick;
        this.approachPhaseStartTick = -1L;
        if (isStandAlive()) {
            this.lastKnownLocation = stand.getLocation().clone();
        }
        invalidateMovementCache();
        recomputeFlightPath();

        applySettings(settings, manager);
        initBossbar(manager);
        enableStandPhysics();

        // Register with performance optimizer
        if (droneManager != null && droneManager.getPerformanceOptimizer() != null) {
            droneManager.getPerformanceOptimizer().registerDrone(droneId);
        }

        // Use BukkitRunnable instead of lambda to avoid synthetic method calls
        this.ticker = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                tickFlight(manager);
            }
        }.runTaskTimer(manager.plugin(), 1L, 1L);
    }

    private void tickFlight(DroneManager manager) {
        if (fixedTarget.getWorld() == null) {
            return;
        }

        long nowTick = Bukkit.getCurrentTick();
        Location expected = expectedLocation(nowTick);

        // Elytra follow check
        boolean isGlidingTarget = false;
        boolean isAirFollowTarget = false;
        if (!exactSocketTarget && settings.followGlidingPlayer() && allowGlideFollow) {
            Player receiver = Bukkit.getPlayer(receiverId);
            if (receiver != null && receiver.isOnline()) {
                if (receiver.getWorld().equals(fixedTarget.getWorld())) {
                    if (receiver.isGliding()) {
                        isGlidingTarget = true;
                        
                        Location current = currentLocation();
                        Location target = receiver.getLocation().clone().add(0.0, 5.0, 0.0);
                        
                        Vector dir = target.toVector().subtract(current.toVector());
                        double dist = dir.length();
                        
                        double step;
                        if (dist <= settings.approachDistance()) {
                            step = settings.approachSpeed();
                        } else {
                            step = settings.speed() * 0.6;
                        }
                        
                        Location newLoc;
                        if (dist <= step) {
                            newLoc = target;
                        } else {
                            newLoc = current.add(dir.normalize().multiply(step));
                        }
                        
                        if (dist > 0.01) {
                            float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                            newLoc.setYaw(yaw);
                        }
                        
                        // Overwrite startLocation and fixedTarget
                        fixedTarget.setX(receiver.getLocation().getX());
                        fixedTarget.setY(receiver.getLocation().getY() + 5.0);
                        fixedTarget.setZ(receiver.getLocation().getZ());
                        
                        startLocation.setX(newLoc.getX());
                        startLocation.setY(newLoc.getY());
                        startLocation.setZ(newLoc.getZ());
                        startLocation.setWorld(newLoc.getWorld());
                        
                        pathComputed = false;
                        flightStartTick = nowTick;
                        approachPhaseStartTick = -1L;
                        invalidateLandingCache();
                        pendingLanding = null;
                        smoothLanding = false;
                        smoothLandingEnd = null;
                        wasGlidingFollowed = true;
                        
                        expected = newLoc;
                    } else if (wasGlidingFollowed && receiver.isOnGround()) {
                        relocateToReceiverOnGround(receiver, manager, nowTick, false);
                        wasGlidingFollowed = false;
                    }
                } else if (wasGlidingFollowed) {
                    wasGlidingFollowed = false;
                }
            }
        }

        // Airborne follow (major falls) — relocate on first ground contact, then land
        if (!exactSocketTarget
                && !isGlidingTarget
                && !landed
                && settings.followAirbornePlayerBeforeLanding()) {
            expireAirborneFollowWindow(nowTick);
            if (allowAirborneFollow || wasAirborneFollowed) {
            Player receiver = Bukkit.getPlayer(receiverId);
            if (receiver != null && receiver.isOnline() && receiver.getWorld().equals(fixedTarget.getWorld())) {
                if (wasAirborneFollowed && receiver.isOnGround()) {
                    relocateToReceiverOnGround(receiver, manager, nowTick, true);
                } else if (canStartAirborneFollow(nowTick)
                        && distanceSquaredToTarget(expected) <= deliveryRadiusSq
                        && isSignificantlyAirborne(receiver)) {
                    isAirFollowTarget = true;

                    Location current = currentLocation();
                    Location target = receiver.getLocation().clone().add(0.0, 5.0, 0.0);

                    Vector dir = target.toVector().subtract(current.toVector());
                    double dist = dir.length();

                    double step;
                    if (dist <= settings.approachDistance()) {
                        step = settings.approachSpeed();
                    } else {
                        step = settings.speed() * 0.6;
                    }

                    Location newLoc;
                    if (dist <= step) {
                        newLoc = target;
                    } else {
                        newLoc = current.add(dir.normalize().multiply(step));
                    }

                    if (dist > 0.01) {
                        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                        newLoc.setYaw(yaw);
                    }

                    fixedTarget.setX(receiver.getLocation().getX());
                    fixedTarget.setY(receiver.getLocation().getY() + 5.0);
                    fixedTarget.setZ(receiver.getLocation().getZ());

                    startLocation.setX(newLoc.getX());
                    startLocation.setY(newLoc.getY());
                    startLocation.setZ(newLoc.getZ());
                    startLocation.setWorld(newLoc.getWorld());

                    pathComputed = false;
                    flightStartTick = nowTick;
                    approachPhaseStartTick = -1L;
                    invalidateLandingCache();
                    pendingLanding = null;
                    smoothLanding = false;
                    smoothLandingEnd = null;
                    wasAirborneFollowed = true;

                    expected = newLoc;
                }
            } else if (wasAirborneFollowed) {
                wasAirborneFollowed = false;
            }
            }
        }

        // Keep virtual position progressing even when chunks are unloaded.
        lastKnownLocation = expected.clone();
        preloadTargetChunkIfNeeded(expected);
        Location standAnchor = landed
                ? (landedLocation != null ? landedLocation : fixedTarget)
                : expected;
        ensureStandPresent(manager, standAnchor);
        boolean standAvailable = isStandAlive();
        if (standAvailable && !expected.getWorld().equals(stand.getWorld())) {
            return;
        }
        
        Location standLocation = standAvailable ? stand.getLocation() : null;
        Location bossbarRef = landed
                ? (landedLocation != null ? landedLocation : (standLocation != null ? standLocation : lastKnownLocation))
                : expected;

        if (!isGlidingTarget && !isAirFollowTarget && !landed && distanceSquaredToTarget(expected) <= deliveryRadiusSq) {
            if (pendingLanding == null) {
                pendingLanding = fixedTarget.clone();
                invalidateLandingCache();
            }
            if (isChunkLoaded(pendingLanding)) {
                // Always use smooth landing - no instant teleportation
                if (!smoothLanding) {
                    smoothLanding = true;
                    smoothLandingStart = expected.clone();
                    smoothLandingStartTick = nowTick;
                    smoothLandingEnd = exactSocketTarget
                            ? pendingLanding.clone()
                            : resolveLandingSpot(pendingLanding);
                }

                Location landingSpot = smoothLandingEnd != null ? smoothLandingEnd : pendingLanding;

                // Continue smooth landing animation
                if (smoothLanding) {
                    long elapsedTicks = nowTick - smoothLandingStartTick;
                    if (elapsedTicks >= smoothLandingDuration) {
                        Location finalSpot = landingSpot.clone();
                        if (!isLandingLocationSafe(finalSpot) && pendingLanding != null) {
                            invalidateLandingCache();
                            finalSpot = exactSocketTarget
                                    ? pendingLanding.clone()
                                    : resolveLandingSpot(pendingLanding);
                        }

                        landAt(manager, finalSpot);
                        invalidateLandingCache();
                        pendingLanding = null;
                        smoothLanding = false;
                        smoothLandingEnd = null;
                        if (!landingNotified) {
                            landingNotified = true;
                            Player receiver = Bukkit.getPlayer(receiverId);
                            if (receiver != null && receiver.isOnline()) {
                                if (socketName != null) {
                                    manager.sendMessage(receiver, "landing-notif-socket", "<socket>", socketName);
                                } else {
                                    int distanceM = (int) Math.round(receiver.getLocation().distance(finalSpot));
                                    manager.sendMessage(receiver, "landing-notif", "<distance>", String.valueOf(distanceM));
                                }
                            }
                        }
                        if (beaconTicker == null) {
                            this.beaconTicker = Bukkit.getScheduler().runTaskTimer(
                                    manager.plugin(),
                                    () -> {
                                        Player onlineReceiver = Bukkit.getPlayer(receiverId);
                                        if (onlineReceiver != null && onlineReceiver.isOnline()) {
                                            renderReceiverBeacon(onlineReceiver);
                                        }
                                        tickContainerIntegration(manager);
                                    },
                                    20L,
                                    20L
                            );
                        }
                    } else {
                        // Animate smooth descent with improved easing
                        double progress = (double) elapsedTicks / smoothLandingDuration;
                        // Use ease-out cubic function for more natural landing
                        double easedProgress = 1 - Math.pow(1 - progress, 3);

                        // Add slight hover effect at the beginning
                        double hoverEffect = Math.sin(progress * Math.PI) * 0.2;

                        Vector startVec = smoothLandingStart.toVector();
                        Vector endVec = landingSpot.toVector();
                        Vector delta = endVec.clone().subtract(startVec);
                        Vector current = startVec.clone().add(delta.multiply(easedProgress));

                        // Apply hover effect only to Y coordinate
                        current.setY(current.getY() + hoverEffect);

                        movementScratch.setWorld(landingSpot.getWorld());
                        movementScratch.setX(current.getX());
                        movementScratch.setY(current.getY());
                        movementScratch.setZ(current.getZ());
                        if (standAvailable && nowTick - lastMovementTick >= MOVEMENT_INTERVAL) {
                            syncStandToExpected(movementScratch, nowTick, true);
                            lastMovementTick = nowTick;
                        }

                        tickAttachedAnimalFollow(movementScratch);
                        if (nowTick - lastParticleUpdateTick >= PARTICLE_UPDATE_INTERVAL) {
                            updateParticleTrailAt(movementScratch);
                        }
                        if (nowTick - lastSoundTick >= SOUND_INTERVAL) {
                            movementScratch.getWorld().playSound(movementScratch, settings.flightSound(), 0.05f, 1.3f);
                            lastSoundTick = nowTick;
                        }
                    }
                }
            }
        }

        // Only continue normal flight if not in landing phase
        if (!landed && !smoothLanding) {
            if (!isChunkLoaded(expected)) {
                if (standAvailable) {
                    parkStandUntilChunkLoads(manager);
                    standAvailable = false;
                }
            } else if (standAvailable) {
                if (nowTick - lastMovementTick >= MOVEMENT_INTERVAL) {
                    syncStandToExpected(expected, nowTick, false);
                    lastMovementTick = nowTick;
                }
                tickAttachedAnimalFollow(expected);
                if (nowTick - lastParticleUpdateTick >= PARTICLE_UPDATE_INTERVAL) {
                    updateParticleTrailAt(expected);
                }
                if (nowTick - lastSoundTick >= SOUND_INTERVAL) {
                    if (standLocation == null) {
                        standLocation = stand.getLocation();
                    }
                    standLocation.getWorld().playSound(standLocation, settings.flightSound(), 0.05f, 1.3f);
                    lastSoundTick = nowTick;
                }
            }
        }
        if (landed && standAvailable) {
            Location landedRef = landedLocation != null ? landedLocation : (standLocation != null ? standLocation : lastKnownLocation);
            tickAttachedAnimalFollow(landedRef);
        }
        if (landed && standAvailable && nowTick - lastHologramUpdateTick >= HOLOGRAM_UPDATE_INTERVAL) {
            if (standLocation != null && isChunkLoaded(standLocation)) {
                updateHologram(nowTick, manager);
            }
        }
        if (nowTick - lastBossBarUpdate >= BOSSBAR_UPDATE_INTERVAL) {
            updateBossBar(manager, bossbarRef, nowTick);
        }

        // Handle collection animation
        if (collectionAnimation && stand != null && !stand.isDead()) {
            long elapsedTicks = nowTick - collectionAnimationStartTick;
            if (elapsedTicks >= collectionAnimationDuration) {
                // Animation complete, destroy the drone
                performDestroy();
            } else {
                // Animate collection effect
                animateCollection(elapsedTicks);
            }
        }
    }

    private void animateCollection(long elapsedTicks) {
        if (stand == null || stand.isDead()) {
            return;
        }

        double progress = (double) elapsedTicks / collectionAnimationDuration;
        double easedProgress = 1 - Math.pow(1 - progress, 3);

        Location current = stand.getLocation().clone();
        double height = easedProgress * 3.0;
        double rotation = elapsedTicks * 0.3;

        Location newPos = current.clone().add(0, height * 0.1, 0);
        newPos.setYaw((float) (current.getYaw() + rotation * 10));

        teleportStand(newPos);

        for (int i = 0; i < 3; i++) {
            double angle = (elapsedTicks * 0.5 + i * 120) * Math.PI / 180;
            double radius = easedProgress * 2.0;
            double x = current.getX() + Math.cos(angle) * radius;
            double z = current.getZ() + Math.sin(angle) * radius;
            double y = current.getY() + height * 0.2;

            Location particleLoc = new Location(current.getWorld(), x, y, z);
            current.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, particleLoc, 5, 0.1, 0.1, 0.1, 0.01);
            current.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, particleLoc, 2, 0.2, 0.2, 0.2, 0.05);
        }

        if (elapsedTicks % 10 == 0) {
            current.getWorld().playSound(current, org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.5f + (float) progress);
        }
    }

    private void landAt(DroneManager manager, Location landing) {
        World world = landing.getWorld();
        if (world == null) {
            return;
        }
        ensureStandPresent(manager, landing);
        if (stand == null || stand.isDead()) {
            return;
        }
        stopStandMotion();
        teleportStand(landing);
        this.landedLocation = landing.clone();
        this.lastKnownLocation = landing.clone();
        stand.setGlowing(settings.glowingEnabled());
        this.landed = true;
        disableStandPhysics();
        this.approachPhaseStartTick = -1L;
        this.distanceAtApproachStart = -1.0;
        // Start despawn timer on landing
        this.lastInteractionTick = Bukkit.getCurrentTick();
        spawnTransportedAnimalsAtLanding();
        updateHologram(Bukkit.getCurrentTick(), manager);
        initBossbar(manager);
    }

    private boolean isSafeGround(World world, int x, int y, int z) {
        org.bukkit.block.Block ground = world.getBlockAt(x, y, z);
        org.bukkit.block.Block feet = world.getBlockAt(x, y + 1, z);
        org.bukkit.block.Block head = world.getBlockAt(x, y + 2, z);
        
        return ground.getType().isSolid() && 
               !ground.isLiquid() &&
               ground.getType() != org.bukkit.Material.BEDROCK && 
               !feet.getType().isSolid() && 
               !feet.isLiquid() &&
               !head.getType().isSolid() && 
               !head.isLiquid();
    }

    private void invalidateLandingCache() {
        cachedLandingSpot = null;
        cachedLandingSpotKey = null;
    }

    private String landingSpotCacheKey(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return "";
        }
        return world.getUID() + ":" + at.getBlockX() + ":" + at.getBlockY() + ":" + at.getBlockZ();
    }

    private Location resolveLandingSpot(Location at) {
        if (exactSocketTarget) {
            return at.clone();
        }
        String key = landingSpotCacheKey(at);
        if (cachedLandingSpot != null && key.equals(cachedLandingSpotKey)) {
            return cachedLandingSpot;
        }
        cachedLandingSpotKey = key;
        cachedLandingSpot = computeLandingFrom(at);
        return cachedLandingSpot;
    }

    private int findSafeLandingY(World world, int x, int startY, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return Math.max(world.getMinHeight() + 1, startY);
        }
        int highest = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (highest >= world.getMinHeight() && highest < world.getMaxHeight() - 2 && isSafeGround(world, x, highest, z)) {
            return highest + 1;
        }
        for (int dy = 0; dy <= 16; dy++) {
            // Check downwards
            int yDown = startY - dy;
            if (yDown >= world.getMinHeight() && yDown < world.getMaxHeight() - 2) {
                if (isSafeGround(world, x, yDown, z)) {
                    return yDown + 1;
                }
            }
            // Check upwards
            if (dy > 0) {
                int yUp = startY + dy;
                if (yUp >= world.getMinHeight() && yUp < world.getMaxHeight() - 2) {
                    if (isSafeGround(world, x, yUp, z)) {
                        return yUp + 1;
                    }
                }
            }
        }
        
        if (world.getName().toLowerCase().contains("nether") && highest >= 120) {
            for (int y = 115; y > world.getMinHeight(); y--) {
                if (isSafeGround(world, x, y, z)) {
                    return y + 1;
                }
            }
        }
        return highest + 1;
    }

    private Location computeLandingFrom(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return at;
        }
        
        if (exactSocketTarget) {
            int x = at.getBlockX();
            int z = at.getBlockZ();
            int y = at.getBlockY();
            if (isSafeGround(world, x, y, z)) {
                return new Location(world, x + 0.5, y + 1.1, z + 0.5);
            }
            int safeY = findSafeLandingY(world, x, y, z);
            return new Location(world, x + 0.5, safeY + 0.1, z + 0.5);
        }
        
        double scanRadius = settings.deliveryRadius();
        int centerX = at.getBlockX();
        int centerY = at.getBlockY();
        int centerZ = at.getBlockZ();
        double scanRadiusSq = scanRadius * scanRadius;

        int centerLandY = findSafeLandingY(world, centerX, centerY, centerZ);
        Location centerSpot = new Location(world, centerX + 0.5, centerLandY + 0.1, centerZ + 0.5);
        if (isLandingLocationSafe(centerSpot)) {
            return centerSpot;
        }

        int bestY = Integer.MIN_VALUE;
        double bestDistanceSq = Double.MAX_VALUE;
        int bestX = centerX;
        int bestZ = centerZ;

        int scanRange = (int) Math.ceil(scanRadius);
        int coarseStep = scanRange > 6 ? 2 : 1;
        for (int dx = -scanRange; dx <= scanRange; dx += coarseStep) {
            for (int dz = -scanRange; dz <= scanRange; dz += coarseStep) {
                int distSq = dx * dx + dz * dz;
                if (distSq > scanRadiusSq) {
                    continue;
                }

                int x = centerX + dx;
                int z = centerZ + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }

                int y = findSafeLandingY(world, x, centerY, z);
                double dy = y - centerY;
                double totalDistSq = distSq + dy * dy;

                if (totalDistSq < bestDistanceSq) {
                    bestDistanceSq = totalDistSq;
                    bestY = y;
                    bestX = x;
                    bestZ = z;
                }
            }
        }

        if (bestY != Integer.MIN_VALUE) {
            if (coarseStep > 1) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = bestX + dx;
                        int z = bestZ + dz;
                        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                            continue;
                        }
                        int distSq = (x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ);
                        if (distSq > scanRadiusSq) {
                            continue;
                        }
                        int y = findSafeLandingY(world, x, centerY, z);
                        double dy = y - centerY;
                        double totalDistSq = distSq + dy * dy;
                        if (totalDistSq < bestDistanceSq) {
                            bestDistanceSq = totalDistSq;
                            bestY = y;
                            bestX = x;
                            bestZ = z;
                        }
                    }
                }
            }
            return new Location(world, bestX + 0.5, bestY + 0.1, bestZ + 0.5);
        }
        
        return new Location(world, at.getX(), at.getY() + 0.1, at.getZ());
    }

    private void relocateToReceiverOnGround(Player receiver, DroneManager manager, long nowTick, boolean airborneCycle) {
        Location receiverLoc = receiver.getLocation();
        fixedTarget.setX(receiverLoc.getX());
        fixedTarget.setY(receiverLoc.getY());
        fixedTarget.setZ(receiverLoc.getZ());

        Location current = currentLocation();
        startLocation.setX(current.getX());
        startLocation.setY(current.getY());
        startLocation.setZ(current.getZ());
        startLocation.setWorld(current.getWorld());

        pathComputed = false;
        flightStartTick = nowTick;
        approachPhaseStartTick = -1L;
        invalidateLandingCache();
        pendingLanding = null;
        smoothLanding = false;
        smoothLandingEnd = null;
        if (airborneCycle) {
            wasAirborneFollowed = false;
            allowAirborneFollow = false;
        }

        manager.sendMessage(receiver, "glide-follow-landed");
    }

    private boolean isLandingLocationSafe(Location landing) {
        World world = landing.getWorld();
        if (world == null) {
            return false;
        }
        int x = landing.getBlockX();
        int y = landing.getBlockY();
        int z = landing.getBlockZ();
        // Landing position is at/just above ground, so validate the block below as "ground"
        return isSafeGround(world, x, y - 1, z);
    }

    /**
     * True when the receiver is clearly airborne (long fall), not a normal jump or short hop.
     */
    private boolean isSignificantlyAirborne(Player receiver) {
        if (receiver.isOnGround() || receiver.isGliding() || receiver.isFlying()) {
            return false;
        }
        if (receiver.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return heightAboveSolidGround(receiver) >= settings.airborneFollowMinHeight();
    }

    private double heightAboveSolidGround(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return 0.0;
        }
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int startY = (int) Math.floor(loc.getY());
        for (int y = startY; y >= world.getMinHeight(); y--) {
            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() && !block.isLiquid()) {
                return loc.getY() - (y + 1);
            }
        }
        return settings.airborneFollowMinHeight();
    }

    private double getCoordinateScale(World world) {
        if (world == null) {
            return 1.0;
        }
        String name = world.getName().toLowerCase();
        if (name.contains("nether")) {
            return 8.0;
        }
        return 1.0;
    }

    private void recomputeFlightPath() {
        if (startLocation.getWorld() == null || fixedTarget.getWorld() == null) {
            pathComputed = false;
            return;
        }

        isCrossDimension = !startLocation.getWorld().equals(fixedTarget.getWorld());

        if (isCrossDimension) {
            String sourceName = startLocation.getWorld().getName().toLowerCase();
            String targetName = fixedTarget.getWorld().getName().toLowerCase();

            crossDimensionH1 = sourceName.contains("nether") ? 120.0 : 280.0;
            crossDimensionH2 = targetName.contains("nether") ? 120.0 : 280.0;

            crossDimensionAscentHeight = Math.abs(crossDimensionH1 - startLocation.getY());
            
            double startScale = getCoordinateScale(startLocation.getWorld());
            double targetScale = getCoordinateScale(fixedTarget.getWorld());

            double startXProj = startLocation.getX() * startScale;
            double startZProj = startLocation.getZ() * startScale;

            double targetXProj = fixedTarget.getX() * targetScale;
            double targetZProj = fixedTarget.getZ() * targetScale;

            double projMidpointX = (startXProj + targetXProj) / 2.0;
            double projMidpointZ = (startZProj + targetZProj) / 2.0;

            crossDimensionMidpointX = projMidpointX / startScale;
            crossDimensionMidpointZ = projMidpointZ / startScale;

            crossDimensionTargetMidpointX = projMidpointX / targetScale;
            crossDimensionTargetMidpointZ = projMidpointZ / targetScale;

            double dx1 = crossDimensionMidpointX - startLocation.getX();
            double dz1 = crossDimensionMidpointZ - startLocation.getZ();
            crossDimensionHorizontalDistance1 = Math.sqrt(dx1 * dx1 + dz1 * dz1);

            double dx2 = fixedTarget.getX() - crossDimensionTargetMidpointX;
            double dz2 = fixedTarget.getZ() - crossDimensionTargetMidpointZ;
            crossDimensionHorizontalDistance2 = Math.sqrt(dx2 * dx2 + dz2 * dz2);

            crossDimensionHorizontalDistance = crossDimensionHorizontalDistance1 + crossDimensionHorizontalDistance2;
            
            crossDimensionDescentHeight = Math.abs(crossDimensionH2 - (fixedTarget.getY() + 0.1));
            
            pathTotalDistance = crossDimensionAscentHeight + crossDimensionHorizontalDistance + crossDimensionDescentHeight;
        } else {
            double targetX = fixedTarget.getX();
            double targetY = fixedTarget.getY() + 0.1;
            double targetZ = fixedTarget.getZ();
            pathDeltaX = targetX - startLocation.getX();
            pathDeltaY = targetY - startLocation.getY();
            pathDeltaZ = targetZ - startLocation.getZ();
            pathTotalDistance = Math.sqrt(pathDeltaX * pathDeltaX + pathDeltaY * pathDeltaY + pathDeltaZ * pathDeltaZ);
        }
        pathComputed = true;
        invalidateMovementCache();
    }

    private void refreshDerivedSettings() {
        double radius = exactSocketTarget ? 0.5D : settings.deliveryRadius();
        deliveryRadiusSq = radius * radius;
    }

    private double distanceSquaredToTarget(Location from) {
        if (from.getWorld() == null || fixedTarget.getWorld() == null || !from.getWorld().equals(fixedTarget.getWorld())) {
            return Double.MAX_VALUE;
        }
        double dx = from.getX() - fixedTarget.getX();
        double dy = from.getY() - (fixedTarget.getY() + 0.1);
        double dz = from.getZ() - fixedTarget.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void invalidateMovementCache() {
        lastTraveledDistanceTick = -1L;
        cachedTraveledDistance = 0.0;
        lastExpectedLocationTick = -1L;
        cachedExpectedLocation = null;
    }

    private Location expectedLocation(long nowTick) {
        if (nowTick == lastExpectedLocationTick && cachedExpectedLocation != null) {
            return cachedExpectedLocation;
        }

        if (!pathComputed) {
            recomputeFlightPath();
        }

        if (isCrossDimension) {
            double traveled = calculateTraveledDistance(nowTick);
            double d = Math.max(0.0D, Math.min(pathTotalDistance, traveled));

            double d1 = crossDimensionAscentHeight;
            double d2 = d1 + crossDimensionHorizontalDistance1;
            double d3 = d2 + crossDimensionHorizontalDistance2;

            Location result;
            if (d < d1) {
                double fraction = d1 > 0.001 ? d / d1 : 1.0;
                double y = startLocation.getY() + fraction * (crossDimensionH1 - startLocation.getY());
                result = new Location(
                        startLocation.getWorld(),
                        startLocation.getX(),
                        y,
                        startLocation.getZ(),
                        fixedTarget.getYaw(),
                        fixedTarget.getPitch()
                );
            } else if (d < d2) {
                double fraction = crossDimensionHorizontalDistance1 > 0.001 ? (d - d1) / crossDimensionHorizontalDistance1 : 1.0;
                double x = startLocation.getX() + fraction * (crossDimensionMidpointX - startLocation.getX());
                double z = startLocation.getZ() + fraction * (crossDimensionMidpointZ - startLocation.getZ());
                result = new Location(
                        startLocation.getWorld(),
                        x,
                        crossDimensionH1,
                        z,
                        fixedTarget.getYaw(),
                        fixedTarget.getPitch()
                );
            } else if (d < d3) {
                double fraction = crossDimensionHorizontalDistance2 > 0.001 ? (d - d2) / crossDimensionHorizontalDistance2 : 1.0;
                double x = crossDimensionTargetMidpointX + fraction * (fixedTarget.getX() - crossDimensionTargetMidpointX);
                double z = crossDimensionTargetMidpointZ + fraction * (fixedTarget.getZ() - crossDimensionTargetMidpointZ);
                result = new Location(
                        fixedTarget.getWorld(),
                        x,
                        crossDimensionH2,
                        z,
                        fixedTarget.getYaw(),
                        fixedTarget.getPitch()
                );
            } else {
                double fraction = crossDimensionDescentHeight > 0.001 ? (d - d3) / crossDimensionDescentHeight : 1.0;
                double targetY = fixedTarget.getY() + 0.1;
                double y = crossDimensionH2 - fraction * (crossDimensionH2 - targetY);
                result = new Location(
                        fixedTarget.getWorld(),
                        fixedTarget.getX(),
                        y,
                        fixedTarget.getZ(),
                        fixedTarget.getYaw(),
                        fixedTarget.getPitch()
                );
            }

            lastExpectedLocationTick = nowTick;
            cachedExpectedLocation = result;
            return result;
        } else {
            double targetX = fixedTarget.getX();
            double targetY = fixedTarget.getY() + 0.1;
            double targetZ = fixedTarget.getZ();

            if (pathTotalDistance <= 0.001D) {
                lastExpectedLocationTick = nowTick;
                if (cachedExpectedLocation == null) {
                    cachedExpectedLocation = new Location(
                            startLocation.getWorld(), targetX, targetY, targetZ,
                            fixedTarget.getYaw(), fixedTarget.getPitch());
                }
                return cachedExpectedLocation;
            }

            double traveled = calculateTraveledDistance(nowTick);
            double factor = Math.min(1.0D, traveled / pathTotalDistance);

            Location result = new Location(
                    startLocation.getWorld(),
                    startLocation.getX() + pathDeltaX * factor,
                    startLocation.getY() + pathDeltaY * factor,
                    startLocation.getZ() + pathDeltaZ * factor,
                    fixedTarget.getYaw(),
                    fixedTarget.getPitch()
            );

            lastExpectedLocationTick = nowTick;
            cachedExpectedLocation = result;
            return result;
        }
    }

    private double calculateTraveledDistance(long nowTick) {
        // Cache result for same tick to avoid redundant calculations
        if (nowTick == lastTraveledDistanceTick) {
            return cachedTraveledDistance;
        }
        
        long elapsedTicks = Math.max(0L, nowTick - flightStartTick);
        long startupTicks = settings.startupSeconds() * 20L;
        long startupPart = Math.min(elapsedTicks, startupTicks);
        long cruisePart = Math.max(0L, elapsedTicks - startupPart);

        if (!pathComputed) {
            recomputeFlightPath();
        }
        double totalDistance = pathTotalDistance;

        // Calculate distance traveled during startup phase
        double startupDistance = startupPart * settings.startupSpeed();
        
        // Calculate distance already covered in cruise phase at normal speed
        double cruiseDistanceCovered = cruisePart * settings.speed();
        
        // Calculate total distance covered so far
        double totalDistanceCovered = startupDistance + cruiseDistanceCovered;
        
        // Calculate remaining distance to target
        double remainingDistanceToTarget = Math.max(0.0, totalDistance - totalDistanceCovered);
        
        double result;
        
        // Check if we're within approach distance of target
        if (remainingDistanceToTarget <= settings.approachDistance()) {
            // In approach phase
            if (approachPhaseStartTick < 0) {
                approachPhaseStartTick = nowTick;
                distanceAtApproachStart = totalDistanceCovered;
            }
            
            long approachPhaseTicks = nowTick - approachPhaseStartTick;
            double approachDistanceCovered = approachPhaseTicks * settings.approachSpeed();
            result = distanceAtApproachStart + approachDistanceCovered;
        } else {
            // Not in approach phase yet
            approachPhaseStartTick = -1L;
            distanceAtApproachStart = -1.0;
            result = startupDistance + cruiseDistanceCovered;
        }
        
        // Cache the result
        lastTraveledDistanceTick = nowTick;
        cachedTraveledDistance = result;
        return result;
    }

    private boolean isChunkLoaded(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        return world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private void tickContainerIntegration(DroneManager manager) {
        if (!manager.settings().containerIntegrationEnabled() || socketName == null || containerIntegrationAborted) {
            return;
        }

        org.bukkit.inventory.Inventory targetInv = resolveContainerInventory(manager);
        if (targetInv == null) {
            return;
        }

        boolean transferredAny = false;
        for (int i = 0; i < inventory.getSize(); i++) {
            org.bukkit.inventory.ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> leftover = targetInv.addItem(item);
                if (leftover.isEmpty()) {
                    inventory.setItem(i, null);
                    transferredAny = true;
                } else {
                    org.bukkit.inventory.ItemStack left = leftover.values().iterator().next();
                    if (left.getAmount() < item.getAmount()) {
                        inventory.setItem(i, left);
                        transferredAny = true;
                    }
                }
            }
        }
        if (transferredAny && isInventoryEmpty() && attachedAnimalTypes.isEmpty()) {
            Player sender = Bukkit.getPlayer(senderId);
            if (sender != null && sender.isOnline()) {
                manager.sendMessage(sender, "container-unload-success", "<socket>", socketName);
            }
            Player receiver = Bukkit.getPlayer(receiverId);
            if (receiver != null && receiver.isOnline() && !receiverId.equals(senderId)) {
                manager.sendMessage(receiver, "container-unload-success", "<socket>", socketName);
            }
            manager.destroyDrone(this, false);
        }
    }

    private org.bukkit.inventory.Inventory resolveContainerInventory(DroneManager manager) {
        if (containerTargetCached && cachedContainerBlock != null) {
            org.bukkit.block.Block block = cachedContainerBlock.getBlock();
            if (block.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
                if (isContainerBlacklisted(manager, block)) {
                    containerIntegrationAborted = true;
                    containerTargetCached = false;
                    cachedContainerBlock = null;
                    return null;
                }
                return holder.getInventory();
            }
            containerTargetCached = false;
            cachedContainerBlock = null;
        }

        org.bukkit.block.Block socketBlock = fixedTarget.getBlock();
        org.bukkit.block.Block bestBlock = null;
        org.bukkit.inventory.Inventory bestInv = null;
        double bestDistSq = Double.MAX_VALUE;
        boolean sawBlacklistedOnly = false;

        int radius = manager.settings().containerIntegrationSearchRadius();
        int centerX = socketBlock.getX();
        int centerY = socketBlock.getY();
        int centerZ = socketBlock.getZ();
        World world = socketBlock.getWorld();
        if (world == null) {
            return null;
        }

        int radiusSq = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (radius > 0 && dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                int minDy = radius > 0 ? -radius : -1;
                int maxDy = radius > 0 ? 1 : 0;
                for (int dy = minDy; dy <= maxDy; dy++) {
                    org.bukkit.block.Block block = world.getBlockAt(centerX + dx, centerY + dy, centerZ + dz);
                    if (!(block.getState() instanceof org.bukkit.inventory.InventoryHolder holder)) {
                        continue;
                    }
                    if (isContainerBlacklisted(manager, block)) {
                        sawBlacklistedOnly = true;
                        continue;
                    }
                    double distSq = block.getLocation().distanceSquared(socketBlock.getLocation());
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        bestBlock = block;
                        bestInv = holder.getInventory();
                    }
                }
            }
        }

        if (bestBlock != null && bestInv != null) {
            cachedContainerBlock = bestBlock.getLocation();
            containerTargetCached = true;
            return bestInv;
        }

        if (sawBlacklistedOnly) {
            containerIntegrationAborted = true;
        }
        return null;
    }

    private boolean isContainerBlacklisted(DroneManager manager, org.bukkit.block.Block block) {
        return manager.settings().containerIntegrationBlacklist().contains(block.getType().name());
    }

    private boolean isInventoryEmpty() {
        for (org.bukkit.inventory.ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    private void updateHologram(long currentTick, DroneManager manager) {
        if (!landed) {
            return;
        }
        // Check hologram update interval
        if (currentTick - lastHologramUpdateTick < HOLOGRAM_UPDATE_INTERVAL) {
            return;
        }
        lastHologramUpdateTick = currentTick;
        if (!settings.hologramEnabled()) {
            if (hologramStand != null && !hologramStand.isDead()) {
                hologramStand.remove();
            }
            hologramStand = null;
            return;
        }
        if (hologramStand == null || hologramStand.isDead()) {
            hologramStand = spawnHologramStand();
            if (hologramStand == null) {
                return;
            }
        }
        Location base = stand != null && !stand.isDead() ? stand.getLocation() : lastKnownLocation;
        Location pos = base.clone().add(0.0, settings.hologramOffset(), 0.0);
        hologramStand.teleport(pos);
        hologramStand.customName(manager.renderHologram(this, currentTick));
        hologramStand.setCustomNameVisible(true);
    }

    private void renderReceiverBeacon(Player receiver) {
        if (!receiver.isOnline() || !landed) {
            return;
        }
        Location loc = (stand != null && !stand.isDead() ? stand.getLocation() : lastKnownLocation).clone().add(0, 0.4, 0);
        if (droneManager != null) {
            for (DroneSettings.ParticleEffect effect : settings.particles()) {
                if (effect.data() != null) {
                    droneManager.getPerformanceOptimizer().spawnParticlesOptimized(
                        loc, effect.particle(), 15, 0.2, 6, 0.2, 0.0, effect.data()
                    );
                } else {
                    droneManager.getPerformanceOptimizer().spawnParticlesOptimized(
                        loc, effect.particle(), 15, 0.2, 6, 0.2, 0.0
                    );
                }
            }
        } else {
            // Fallback
            for (DroneSettings.ParticleEffect effect : settings.particles()) {
                if (effect.data() != null) {
                    receiver.spawnParticle(effect.particle(), loc, 15, 0.2, 6, 0.2, 0.0, effect.data());
                } else {
                    receiver.spawnParticle(effect.particle(), loc, 15, 0.2, 6, 0.2, 0.0);
                }
            }
        }
    }

    private void updateParticleTrailAt(Location ref) {
        if (ref.getWorld() == null) {
            return;
        }

        long currentTick = Bukkit.getCurrentTick();
        lastParticleUpdateTick = currentTick;

        if (droneManager != null && droneManager.getPerformanceOptimizer() != null) {
            if (droneManager.getPerformanceOptimizer().shouldThrottlePerformance()) {
                return;
            }
            if (!droneManager.getPerformanceOptimizer().shouldSpawnParticles(droneId)) {
                return;
            }
        }

        movementScratch.setWorld(ref.getWorld());
        movementScratch.setX(ref.getX());
        movementScratch.setY(ref.getY() + settings.particleYOffset());
        movementScratch.setZ(ref.getZ());
        particleTrail.addFirst(movementScratch.clone());
        
        // Limit trail length to reduce memory and iteration cost
        int maxTrailLength = Math.min(settings.particleTrailLength(), 20);
        while (particleTrail.size() > maxTrailLength) {
            particleTrail.removeLast();
        }

        int spawned = 0;
        int maxSpawnPoints = 2;
        for (Location point : particleTrail) {
            if (spawned >= maxSpawnPoints) {
                break;
            }
            int count = Math.max(1, settings.particleCount() - spawned);
            if (droneManager != null && droneManager.getPerformanceOptimizer() != null) {
                var optimizer = droneManager.getPerformanceOptimizer();
                DroneSettings.ParticleEffect effect = settings.particles().get(spawned % settings.particles().size());
                if (effect.data() != null) {
                    optimizer.spawnParticlesOptimized(point, effect.particle(), count, 0.05, 0.05, 0.05, 0.0, effect.data());
                } else {
                    optimizer.spawnParticlesOptimized(point, effect.particle(), count, 0.05, 0.05, 0.05, 0.0);
                }
            }
            spawned++;
        }
    }

    private void tickAttachedAnimalFollow(Location expectedDroneLocation) {
        if (stand == null || stand.isDead() || standParked) {
            return;
        }
        List<LivingEntity> animals = attachedAnimals();
        if (animals.isEmpty()) {
            return;
        }
        
        Location standLocation = stand.getLocation();
        double targetX = expectedDroneLocation.getX();
        double targetY = expectedDroneLocation.getY() - 0.2;
        double targetZ = expectedDroneLocation.getZ();
        double standX = standLocation.getX();
        double standY = standLocation.getY();
        double standZ = standLocation.getZ();
        
        for (LivingEntity animal : animals) {
            if (animal.isDead()) {
                continue;
            }
            if (!animal.getWorld().equals(stand.getWorld())) {
                animal.teleport(new Location(stand.getWorld(), targetX, targetY, targetZ));
                continue;
            }
            
            Location animalLoc = animal.getLocation();
            double animalX = animalLoc.getX();
            double animalY = animalLoc.getY();
            double animalZ = animalLoc.getZ();
            
            // Calculate squared distances to avoid expensive sqrt
            double dx = animalX - targetX;
            double dy = animalY - targetY;
            double dz = animalZ - targetZ;
            double toTargetSq = dx * dx + dy * dy + dz * dz;
            
            boolean teleported = false;
            if (toTargetSq > 16.0D && isChunkLoaded(expectedDroneLocation)) {
                animal.teleport(new Location(expectedDroneLocation.getWorld(), targetX, targetY, targetZ));
                teleported = true;
            }
            
            // Calculate squared distance to stand
            double sdx = animalX - standX;
            double sdy = animalY - standY;
            double sdz = animalZ - standZ;
            double toStandSq = sdx * sdx + sdy * sdy + sdz * sdz;
            
            // Avoid repeated re-leash attempts
            if (!teleported && toStandSq > 100.0D) {
                continue;
            }
            
            if (!animal.isLeashed() || animal.getLeashHolder() == null || !animal.getLeashHolder().getUniqueId().equals(stand.getUniqueId())) {
                try {
                    animal.setLeashHolder(stand);
                } catch (IllegalStateException ignored) {
                    // Some entities can reject leash changes based on state.
                }
            }
        }
    }

    public void destroy() {
        // Start collection animation if enabled and not already animating
        if (settings.collectionAnimationEnabled() && !collectionAnimation && stand != null && !stand.isDead()) {
            collectionAnimation = true;
            collectionAnimationStart = stand.getLocation().clone();
            collectionAnimationStartTick = Bukkit.getCurrentTick();
            return; // Don't destroy immediately, let animation complete
        }

        // Normal destroy without animation
        performDestroy();
    }

    private void performDestroy() {
        // Unregister from performance optimizer
        if (droneManager != null && droneManager.getPerformanceOptimizer() != null) {
            droneManager.getPerformanceOptimizer().unregisterDrone(droneId);
        }
        
        releaseLeashedAnimal();
        if (ticker != null) {
            ticker.cancel();
        }
        if (beaconTicker != null) {
            beaconTicker.cancel();
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
        if (hologramStand != null && !hologramStand.isDead()) {
            hologramStand.remove();
        }
        if (isStandAlive()) {
            disableStandPhysics();
            stand.remove();
        }
        stand = null;
        standId = null;
        collectionAnimation = false;
    }

    public static ArmorStand spawnDroneEntity(Location at, DroneSettings settings) {
        Location spawnLoc = at.clone();
        ArmorStand stand = (ArmorStand) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setInvulnerable(true);
        stand.setGravity(false);
        stand.setMarker(false);
        stand.setSmall(true);
        stand.setCanTick(false);
        stand.setSilent(true);
        stand.setPersistent(true);
        stand.setCollidable(false);
        stand.setCustomNameVisible(false);
        applyDroneHelmet(stand, settings);
        return stand;
    }

    private static void applyDroneHelmet(ArmorStand stand, DroneSettings settings) {
        ItemStack customModel = CustomItemHook.getCustomItem(settings);
        if (customModel != null) {
            stand.getEquipment().setHelmet(customModel);
        } else {
            stand.getEquipment().setHelmet(createSkullStatic(settings.skullTexture()));
        }
    }

    private void teleportStand(Location loc) {
        if (stand != null && !stand.isDead() && loc != null) {
            stand.teleport(loc);
        }
    }

    private static ItemStack createSkullStatic(String texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gold>Drone</gold>"));
            applyTexture(meta, texture);
            head.setItemMeta(meta);
        }
        return head;
    }

    private static void applyTexture(SkullMeta meta, String base64Texture) {
        if (base64Texture == null || base64Texture.isBlank()) {
            return;
        }
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "drone");
            profile.setProperty(new ProfileProperty("textures", base64Texture.trim()));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // keep default skull when profile api fails
        }
    }

    private void initBossbar(DroneManager manager) {
        if (!settings.bossbarEnabled()) {
            return;
        }
        
        boolean shouldInit = false;
        Player receiver = Bukkit.getPlayer(receiverId);
        if (receiver != null) {
            shouldInit = true;
        } else if (socketName != null) {
            DeliverySocket socket = manager.getSocketRepository().getSocket(receiverId, socketName);
            if (socket != null) {
                for (UUID trustedId : socket.trustedPlayers()) {
                    if (Bukkit.getPlayer(trustedId) != null) {
                        shouldInit = true;
                        break;
                    }
                }
            }
        }
        
        if (!shouldInit) {
            return;
        }
        
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar("Drone", settings.bossbarColor(), org.bukkit.boss.BarStyle.SEGMENTED_10);
        }
        
        if (receiver != null) {
            bossBar.addPlayer(receiver);
        }
        bossBar.setVisible(true);
    }

    private void updateBossBar(DroneManager manager, Location droneRefLocation, long currentTick) {
        if (!settings.bossbarEnabled()) {
            if (bossBar != null) {
                bossBar.removeAll();
                bossBar.setVisible(false);
            }
            return;
        }
        lastBossBarUpdate = currentTick;

        if (bossBar == null) {
            initBossbar(manager);
            if (bossBar == null) {
                return;
            }
        }
        
        if (droneRefLocation.getWorld() == null) {
            return;
        }

        // Add online players to bossbar once per second
        if (currentTick % 20 == 0) {
            Player receiver = Bukkit.getPlayer(receiverId);
            if (receiver != null && !bossBar.getPlayers().contains(receiver)) {
                bossBar.addPlayer(receiver);
            }
            if (socketName != null) {
                DeliverySocket socket = manager.getSocketRepository().getSocket(receiverId, socketName);
                if (socket != null) {
                    for (UUID trustedId : socket.trustedPlayers()) {
                        Player trusted = Bukkit.getPlayer(trustedId);
                        if (trusted != null && !bossBar.getPlayers().contains(trusted)) {
                            bossBar.addPlayer(trusted);
                        }
                    }
                }
            }
        }

        if (bossBar.getPlayers().isEmpty()) {
            return;
        }

        int distanceM;
        Player receiver = Bukkit.getPlayer(receiverId);
        if (socketName == null && receiver != null && receiver.getLocation().getWorld().equals(droneRefLocation.getWorld())) {
            Location receiverLoc = receiver.getLocation();
            double dx = receiverLoc.getX() - droneRefLocation.getX();
            double dy = receiverLoc.getY() - droneRefLocation.getY();
            double dz = receiverLoc.getZ() - droneRefLocation.getZ();
            distanceM = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
        } else {
            double traveled = calculateTraveledDistance(currentTick);
            distanceM = (int) Math.max(0.0, pathTotalDistance - traveled);
        }
        long eta = etaSeconds(currentTick);
        if (distanceM == lastBossBarDistanceM && eta == lastBossBarEta) {
            return;
        }
        lastBossBarDistanceM = distanceM;
        lastBossBarEta = eta;
        bossBar.setProgress(1.0D);
        if (socketName != null) {
            bossBar.setTitle(manager.renderBossbarSocket(socketName, eta));
        } else {
            bossBar.setTitle(manager.renderBossbar(distanceM, eta));
        }
    }

    private long etaSeconds(long nowTick) {
        if (landed) {
            return 0L;
        }

        if (!pathComputed) {
            recomputeFlightPath();
        }

        double traveled = calculateTraveledDistance(nowTick);
        double remainingDistance = Math.max(0.0D, pathTotalDistance - traveled);

        if (remainingDistance * remainingDistance <= deliveryRadiusSq) {
            return 3L;
        }

        double currentSpeed = getCurrentSpeed(nowTick, remainingDistance);
        if (currentSpeed <= 0.001) {
            return Long.MAX_VALUE;
        }

        return (long) Math.ceil(remainingDistance / (currentSpeed * 20.0D)) + 3L;
    }

    private double getCurrentSpeed(long nowTick, double remainingPathDistance) {
        long elapsedTicks = Math.max(0L, nowTick - flightStartTick);
        long startupTicks = settings.startupSeconds() * 20L;

        if (elapsedTicks < startupTicks) {
            return settings.startupSpeed();
        }

        if (remainingPathDistance <= settings.approachDistance()) {
            return settings.approachSpeed();
        }

        return settings.speed();
    }


    private ArmorStand spawnHologramStand() {
        if (stand == null || stand.isDead()) {
            return null;
        }
        World world = stand.getWorld();
        ArmorStand holo = (ArmorStand) world.spawnEntity(stand.getLocation().clone().add(0, settings.hologramOffset(), 0), EntityType.ARMOR_STAND);
        holo.setVisible(false);
        holo.setInvulnerable(true);
        holo.setGravity(false);
        holo.setMarker(true);
        holo.setSmall(true);
        holo.setCanTick(false);
        holo.setSilent(true);
        holo.setPersistent(true);
        return holo;
    }

    private List<LivingEntity> attachedAnimals() {
        if (spawnedTransportAnimalIds.isEmpty()) {
            return List.of();
        }
        List<LivingEntity> result = new ArrayList<>();
        for (UUID animalId : spawnedTransportAnimalIds) {
            Entity entity = Bukkit.getEntity(animalId);
            if (entity instanceof LivingEntity living) {
                result.add(living);
            }
        }
        return result;
    }

    private void ensureStandPresent(DroneManager manager, Location preferredLocation) {
        if (isStandAlive()) {
            World standWorld = stand.getWorld();
            if (standWorld != null && !standWorld.equals(preferredLocation.getWorld())) {
                if (isChunkLoaded(preferredLocation)) {
                    teleportStand(preferredLocation);
                } else {
                    parkStandUntilChunkLoads(manager);
                }
            }
            if (isStandAlive()) {
                standId = stand.getUniqueId();
                return;
            }
        }
        if (!isChunkLoaded(preferredLocation)) {
            return;
        }
        ArmorStand respawned = spawnDroneEntity(preferredLocation, settings);
        if (respawned == null) {
            return;
        }
        UUID previous = standId;
        this.stand = respawned;
        this.standId = respawned.getUniqueId();
        this.lastKnownLocation = preferredLocation.clone();
        this.standParked = false;
        if (!landed) {
            enableStandPhysics();
        }
        if (landed) {
            stand.setGlowing(settings.glowingEnabled());
            spawnTransportedAnimalsAtLanding();
        }
        manager.onDroneStandChanged(this, previous, this.standId);
        attachLeashedAnimal();
    }

    private void preloadTargetChunkIfNeeded(Location expected) {
        if (!forceTargetChunkLoad || targetChunkPreloaded || landed) {
            return;
        }
        long nowTick = Bukkit.getCurrentTick();
        if (nowTick - lastChunkPreloadCheckTick < CHUNK_PRELOAD_CHECK_INTERVAL) {
            return;
        }
        lastChunkPreloadCheckTick = nowTick;
        World world = fixedTarget.getWorld();
        if (world == null) {
            return;
        }
        double triggerDistance = Math.max(settings.deliveryRadius() * 4.0D, 64.0D);
        if (distanceSquaredToTarget(expected) > triggerDistance * triggerDistance) {
            return;
        }
        int chunkX = fixedTarget.getBlockX() >> 4;
        int chunkZ = fixedTarget.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.getChunkAt(chunkX, chunkZ).load();
        }
        targetChunkPreloaded = true;
    }

    private void spawnTransportedAnimalsAtLanding() {
        if (!animalsOnlyDelivery || stand == null || stand.isDead()) {
            return;
        }
        if (attachedAnimalTypes.isEmpty()) {
            return;
        }
        if (spawnedTransportAnimalIds.isEmpty()) {
            for (EntityType type : attachedAnimalTypes) {
                if (!type.isAlive() || type == EntityType.PLAYER || type == EntityType.ARMOR_STAND) {
                    continue;
                }
                Entity entity = stand.getWorld().spawnEntity(stand.getLocation().clone().add(0.0, 0.2, 0.0), type);
                if (entity instanceof LivingEntity living) {
                    living.setInvulnerable(true);
                    spawnedTransportAnimalIds.add(living.getUniqueId());
                } else {
                    entity.remove();
                }
            }
        }
        attachLeashedAnimal();
    }

    private void parkStandUntilChunkLoads(DroneManager manager) {
        if (stand == null || stand.isDead()) {
            return;
        }
        UUID previous = standId;
        lastKnownLocation = stand.getLocation().clone();
        stand.remove();
        stand = null;
        standId = null;
        standParked = true;
        if (hologramStand != null && !hologramStand.isDead()) {
            hologramStand.remove();
        }
        hologramStand = null;
        manager.onDroneStandChanged(this, previous, null);
    }

    public Location getFixedTarget() { return fixedTarget; }
    public long getFlightStartTick() { return flightStartTick; }
    public long getDeliveryFlightStartTick() { return deliveryFlightStartTick; }
    public boolean isForceTargetChunkLoad() { return forceTargetChunkLoad; }
    public boolean isExactSocketTarget() { return exactSocketTarget; }
    public boolean isStandParked() { return standParked; }

    public static DeliveryDrone fromPersistentData(
            UUID droneId, UUID senderId, UUID receiverId, String receiverName,
            Location fixedTarget, Location startLocation, Location lastKnownLocation,
            long flightStartTick, long deliveryFlightStartTick, ItemStack[] inventoryContents, List<EntityType> attachedAnimalTypes,
            boolean animalsOnlyDelivery, boolean forceTargetChunkLoad, boolean exactSocketTarget,
            String socketName, boolean landed, boolean openedByReceiver,
            long lastInteractionTick, boolean standParked, DroneManager manager
    ) {
        int invSize = inventoryContents.length > 0 && inventoryContents.length % 9 == 0 ? inventoryContents.length : 27;
        Inventory inv = Bukkit.createInventory(null, invSize, manager.componentMessage("drone-inventory-title", null, null));
        inv.setContents(inventoryContents);
        
        // Temporarily null stand, we'll restore it
        DeliveryDrone drone = new DeliveryDrone(
                droneId, senderId, receiverId, receiverName, fixedTarget,
                inv, attachedAnimalTypes, animalsOnlyDelivery, forceTargetChunkLoad,
                exactSocketTarget, socketName, manager.settings(), null, flightStartTick
        );
        
        drone.droneManager = manager;
        drone.startLocation.setX(startLocation.getX());
        drone.startLocation.setY(startLocation.getY());
        drone.startLocation.setZ(startLocation.getZ());
        drone.startLocation.setWorld(startLocation.getWorld());
        drone.startLocation.setYaw(startLocation.getYaw());
        drone.startLocation.setPitch(startLocation.getPitch());
        
        drone.lastKnownLocation = lastKnownLocation;
        drone.flightStartTick = flightStartTick;
        drone.deliveryFlightStartTick = deliveryFlightStartTick;
        drone.landed = landed;
        drone.openedByReceiver = openedByReceiver;
        drone.lastInteractionTick = lastInteractionTick;
        drone.standParked = standParked;
        
        drone.recomputeFlightPath();
        drone.initBossbar(manager);
        
        if (!standParked) {
            drone.ensureStandPresent(manager, lastKnownLocation);
        }
        
        if (landed) {
            if (drone.stand != null && !drone.stand.isDead()) {
                drone.landedLocation = drone.stand.getLocation().clone();
            } else {
                drone.landedLocation = lastKnownLocation.clone();
            }
            if (manager.settings().hologramEnabled()) {
                drone.updateHologram(Bukkit.getCurrentTick(), manager);
            }
        }
        
        drone.ticker = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                drone.tickFlight(manager);
            }
        }.runTaskTimer(manager.plugin(), 1L, 1L);
        
        if (landed && !openedByReceiver) {
            drone.beaconTicker = Bukkit.getScheduler().runTaskTimer(
                    manager.plugin(),
                    () -> {
                        Player onlineReceiver = Bukkit.getPlayer(receiverId);
                        if (onlineReceiver != null && onlineReceiver.isOnline()) {
                            drone.renderReceiverBeacon(onlineReceiver);
                        }
                        drone.tickContainerIntegration(manager);
                    },
                    20L,
                    20L
            );
        }
        
        // Register with performance optimizer
        if (manager.getPerformanceOptimizer() != null) {
            manager.getPerformanceOptimizer().registerDrone(droneId);
        }
        
        return drone;
    }
}
