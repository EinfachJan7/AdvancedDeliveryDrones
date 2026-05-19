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
import org.bukkit.World;
import org.bukkit.boss.BossBar;
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
    private Location landedLocation;
    private boolean openedByReceiver;
    private long lastInteractionTick;
    private BukkitTask ticker;
    private BukkitTask beaconTicker;
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
    
    // Performance caches
    private long lastTraveledDistanceTick = -1L;
    private double cachedTraveledDistance = 0.0;
    private long lastParticleUpdateTick = -1L;
    private static final long PARTICLE_UPDATE_INTERVAL = 4L;
    private long lastHologramUpdateTick = -1L;
    private static final long HOLOGRAM_UPDATE_INTERVAL = 60L; // Hologram every 3 seconds (60 ticks)
    private long lastExpectedLocationTick = -1L;
    private Location cachedExpectedLocation = null;
    private long lastTeleportTick = -1L;
    private static final long TELEPORT_INTERVAL = 1L;
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
        this.startLocation = stand.getLocation().clone();
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
        this.standId = stand.getUniqueId();
        this.lastKnownLocation = stand.getLocation().clone();
        this.lastInteractionTick = createdTick;
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
            stand.getEquipment().setHelmet(createSkullStatic(settings.skullTexture()));
        }
        if (landed) {
            if (stand != null && !stand.isDead()) {
                stand.setGlowing(true);
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
        int duration = settings.launchAnimationSeconds();
        int totalTicks = duration * 20;
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
                
                // Smooth ease-in-out for natural acceleration
                double easedProgress = progress < 0.5 
                    ? 4 * progress * progress * progress 
                    : 1 - Math.pow(-2 * progress + 2, 3) / 2;
                
                // Calculate rise height (1.5 blocks total - more subtle)
                double riseHeight = easedProgress * 1.5;
                
                // Update drone position - slowly rise up
                Location newPos = startAnimLocation.clone();
                newPos.setY(startAnimLocation.getY() + riseHeight);
                
                // Gentle hover effect (slower sine wave)
                double hoverOffset = Math.sin(progress * Math.PI * 1.5) * 0.05;
                newPos.setY(newPos.getY() + hoverOffset);
                
                // Slow steady rotation (1.5 rotations total)
                float spinAngle = (float) (startYaw + (easedProgress * 360 * 1.5));
                newPos.setYaw(spinAngle);
                
                stand.teleport(newPos);
                
                Location center = newPos.clone();

                // Refined spiral particle effect
                double angle = ticks * 0.3;
                double radius = 2.0 * (1 - progress * 0.5); // Larger radius, slower shrink

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

                // Gentle rising clouds below
                if (ticks % 6 == 0) {
                    for (int i = 0; i < 1; i++) {
                        double cloudAngle = Math.random() * Math.PI * 2;
                        double dist = Math.random() * 0.3;
                        Location below = center.clone().add(
                            Math.cos(cloudAngle) * dist, 
                            -0.3 - (Math.random() * 0.2), 
                            Math.sin(cloudAngle) * dist
                        );
                        center.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, below, 1, 0.03, 0.05, 0.03, 0.01);
                    }
                }

                // Subtle glow particles
                if (ticks % 12 == 0) {
                    center.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, center.clone().add(0, 0.3, 0), 3, 0.3, 0.3, 0.3, 0.01);
                }

                // Play launch sound at start
                if (ticks == 0) {
                    center.getWorld().playSound(center, settings.launchSound(), settings.launchSoundVolume() * 0.8f, 0.9f);
                }

                // Subtle rising sound
                if (ticks % 15 == 0) {
                    center.getWorld().playSound(center, settings.flightSound(), 0.05f, 0.9f + (float) progress * 0.2f);
                }

                ticks++;

                // Animation complete - start the actual flight
                if (ticks >= totalTicks) {
                    cancel();

                    // Subtle launch effect
                    center.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, center, 5, 0.4, 0.4, 0.4, 0.08);
                    center.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, center, 25, 0.4, 0.4, 0.4, 0.25);
                    center.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, center, 12, 0.5, 0.5, 0.5, 0.03);
                    center.getWorld().playSound(center, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);

                    startLocation.set(center.getX(), center.getY(), center.getZ());
                    startLocation.setYaw(center.getYaw());
                    startLocation.setPitch(center.getPitch());

                    startFlightInternal(manager);
                }
            }
        }

        new LaunchAnimationTask().runTaskTimer(manager.plugin(), 0L, 1L);
    }

    private void startFlightInternal(DroneManager manager) {
        long nowTick = Bukkit.getCurrentTick();
        this.flightStartTick = nowTick;
        this.approachPhaseStartTick = -1L;
        invalidateMovementCache();
        recomputeFlightPath();

        applySettings(settings, manager);
        initBossbar(manager);

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
        // Keep virtual position progressing even when chunks are unloaded.
        // Reference the same location object instead of cloning
        lastKnownLocation = expected;
        preloadTargetChunkIfNeeded(expected);
        Location standAnchor = landed
                ? computeLandingFrom(landedLocation != null ? landedLocation : fixedTarget)
                : expected;
        ensureStandPresent(manager, standAnchor);
        boolean standAvailable = stand != null && !stand.isDead();
        if (standAvailable && !expected.getWorld().equals(stand.getWorld())) {
            return;
        }
        
        // Cache stand location to avoid multiple getLocation() calls
        Location standLocation = standAvailable ? stand.getLocation() : null;
        Location bossbarRef = landed
                ? (landedLocation != null ? landedLocation : (standLocation != null ? standLocation : lastKnownLocation))
                : expected;

        if (!landed && distanceSquaredToTarget(expected) <= deliveryRadiusSq) {
            if (pendingLanding == null) {
                pendingLanding = fixedTarget.clone();
            }
            if (isChunkLoaded(pendingLanding)) {
                Location landingSpot = exactSocketTarget ? pendingLanding.clone() : computeLandingFrom(pendingLanding);
                
                // Always use smooth landing - no instant teleportation
                if (!smoothLanding) {
                    // Start smooth landing animation
                    smoothLanding = true;
                    smoothLandingStart = expected.clone();
                    smoothLandingStartTick = nowTick;
                }
                
                // Continue smooth landing animation
                if (smoothLanding) {
                    long elapsedTicks = nowTick - smoothLandingStartTick;
                    if (elapsedTicks >= smoothLandingDuration) {
                        // Landing animation complete
                        landAt(manager, landingSpot);
                        pendingLanding = null;
                        smoothLanding = false;
                        if (!landingNotified) {
                            landingNotified = true;
                            Player receiver = Bukkit.getPlayer(receiverId);
                            if (receiver != null && receiver.isOnline()) {
                                if (socketName != null) {
                                    manager.sendMessage(receiver, "landing-notif-socket", "<socket>", socketName);
                                } else {
                                    manager.sendMessage(receiver, "landing-notif", "<radius>", String.valueOf((int) settings.deliveryRadius()));
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
                        Vector current = startVec.clone().add(endVec.clone().subtract(startVec).multiply(easedProgress));

                        // Apply hover effect only to Y coordinate
                        current.setY(current.getY() + hoverEffect);

                        Location animatedPos = new Location(landingSpot.getWorld(), current.getX(), current.getY(), current.getZ());
                        ensureStandPresent(manager, animatedPos);
                        if (stand != null && !stand.isDead()) {
                            stand.teleport(animatedPos);
                        }

                        tickAttachedAnimalFollow(animatedPos);
                        updateParticleTrail();
                        if (nowTick - lastSoundTick >= SOUND_INTERVAL) {
                            // Cache stand location for sound playing
                            Location soundLoc = stand != null && !stand.isDead() ? stand.getLocation() : animatedPos;
                            soundLoc.getWorld().playSound(soundLoc, settings.flightSound(), 0.05f, 1.3f);
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
                if (nowTick - lastTeleportTick >= TELEPORT_INTERVAL) {
                    stand.teleport(expected);
                    lastTeleportTick = nowTick;
                }
                tickAttachedAnimalFollow(expected);
                updateParticleTrail();
                if (nowTick - lastSoundTick >= SOUND_INTERVAL) {
                    // Cache stand properties to avoid multiple calls
                    if (standLocation == null) {
                        standLocation = stand.getLocation();
                    }
                    standLocation.getWorld().playSound(standLocation, settings.flightSound(), 0.05f, 1.3f);
                    lastSoundTick = nowTick;
                }
            }
        }
        if (landed && standAvailable) {
            Location landedRef = landedLocation != null ? landedLocation : (stand != null && !stand.isDead() ? stand.getLocation() : lastKnownLocation);
            tickAttachedAnimalFollow(landedRef);
        }
        if (landed && standAvailable && nowTick - lastHologramUpdateTick >= HOLOGRAM_UPDATE_INTERVAL) {
            if (isChunkLoaded(stand.getLocation())) {
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
        if (stand == null || stand.isDead()) return;

        double progress = (double) elapsedTicks / collectionAnimationDuration;
        // Use ease-out cubic for smooth collection
        double easedProgress = 1 - Math.pow(1 - progress, 3);

        // Animate upward spiral and shrink
        Location current = stand.getLocation().clone();
        double height = easedProgress * 3.0; // Rise 3 blocks
        double rotation = elapsedTicks * 0.3; // Rotation speed

        Location newPos = current.add(0, height * 0.1, 0); // Gradual rise
        newPos.setYaw((float) (current.getYaw() + rotation * 10));
        
        // Apply scale effect through visual means
        stand.teleport(newPos);
        
        // Create collection particles
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

        // Play collection sound
        if (elapsedTicks % 10 == 0) { // Every 0.5 seconds
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
        stand.teleport(landing);
        this.landedLocation = landing.clone();
        this.lastKnownLocation = landing.clone();
        stand.setGlowing(true);
        this.landed = true;
        this.approachPhaseStartTick = -1L;
        // Start despawn timer on landing
        this.lastInteractionTick = Bukkit.getCurrentTick();
        spawnTransportedAnimalsAtLanding();
        updateHologram(Bukkit.getCurrentTick(), manager);
        initBossbar(manager);
    }

    private Location computeLandingFrom(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return at;
        }
        
        if (exactSocketTarget) {
            // For sockets, use exact position + 1 block height
            int x = at.getBlockX();
            int z = at.getBlockZ();
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            y = Math.max(y, world.getMinHeight() + 1);
            return new Location(world, x + 0.5, y + 0.1, z + 0.5);
        }
        
        // For player targets, scan for highest safe position in radius
        double scanRadius = settings.deliveryRadius();
        int centerX = at.getBlockX();
        int centerZ = at.getBlockZ();
        double scanRadiusSq = scanRadius * scanRadius;
        
        int bestY = world.getMinHeight();
        double bestDistanceSq = Double.MAX_VALUE;
        int bestX = centerX;
        int bestZ = centerZ;
        
        // Scan in a square around the target for best landing spot
        int scanRange = (int) Math.ceil(scanRadius);
        for (int dx = -scanRange; dx <= scanRange; dx++) {
            for (int dz = -scanRange; dz <= scanRange; dz++) {
                // Check if within radius (using squared distance to avoid sqrt)
                int distSq = dx * dx + dz * dz;
                if (distSq > scanRadiusSq) continue;
                
                int x = centerX + dx;
                int z = centerZ + dz;
                
                // Get highest safe block at this position
                int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                y = Math.max(y, world.getMinHeight() + 1);
                
                // Check if this spot is better (higher and closer to target)
                if (y > bestY || (y == bestY && distSq < bestDistanceSq)) {
                    bestY = y;
                    bestDistanceSq = distSq;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        
        return new Location(world, bestX + 0.5, bestY + 0.1, bestZ + 0.5);
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
            }
            
            long approachPhaseTicks = nowTick - approachPhaseStartTick;
            double approachDistanceCovered = approachPhaseTicks * settings.approachSpeed();
            double distanceBeforeApproach = Math.max(0.0, totalDistance - settings.approachDistance());
            result = distanceBeforeApproach + approachDistanceCovered;
        } else {
            // Not in approach phase yet
            approachPhaseStartTick = -1L;
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

    private void updateParticleTrail() {
        if (stand == null || stand.isDead()) {
            return;
        }
        
        // Check particle update interval to reduce processing
        long currentTick = Bukkit.getCurrentTick();
        if (currentTick - lastParticleUpdateTick < PARTICLE_UPDATE_INTERVAL) {
            return;
        }
        lastParticleUpdateTick = currentTick;
        
        // Check performance optimization
        if (droneManager != null && droneManager.getPerformanceOptimizer() != null) {
            // Skip particles if performance should be throttled
            if (droneManager.getPerformanceOptimizer().shouldThrottlePerformance()) {
                return;
            }
            
            // Check particle cooldown
            if (!droneManager.getPerformanceOptimizer().shouldSpawnParticles(droneId)) {
                return;
            }
        }
        
        Location standLoc = stand.getLocation();
        Location now = new Location(standLoc.getWorld(), standLoc.getX(), 
            standLoc.getY() + settings.particleYOffset(), standLoc.getZ());
        particleTrail.addFirst(now);
        
        // Limit trail length to reduce memory and iteration cost
        int maxTrailLength = Math.min(settings.particleTrailLength(), 20);
        while (particleTrail.size() > maxTrailLength) {
            particleTrail.removeLast();
        }

        int index = 0;
        for (Location point : particleTrail) {
            // Skip every other particle point for better performance
            if (index % 2 == 0) {
                int count = Math.max(1, settings.particleCount() - (index / 4));
                double spread = 0.02 + (index * 0.01);
                // Use performance optimizer for particles
                if (droneManager != null) {
                    for (DroneSettings.ParticleEffect effect : settings.particles()) {
                        if (effect.data() != null) {
                            droneManager.getPerformanceOptimizer().spawnParticlesOptimized(
                                point, effect.particle(), count, spread, spread, spread, 0.0, effect.data()
                            );
                        } else {
                            droneManager.getPerformanceOptimizer().spawnParticlesOptimized(
                                point, effect.particle(), count, spread, spread, spread, 0.0
                            );
                        }
                    }
                } else {
                    // Fallback if droneManager is not set
                    for (DroneSettings.ParticleEffect effect : settings.particles()) {
                        if (effect.data() != null) {
                            point.getWorld().spawnParticle(effect.particle(), point, count, spread, spread, spread, 0.0, effect.data());
                        } else {
                            point.getWorld().spawnParticle(effect.particle(), point, count, spread, spread, spread, 0.0);
                        }
                    }
                }
            }
            index++;
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
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }
        stand = null;
        standId = null;
        collectionAnimation = false;
    }

    public static ArmorStand spawnDroneEntity(Location at, String skullTexture) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        ArmorStand stand = (ArmorStand) world.spawnEntity(at, EntityType.ARMOR_STAND);
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
        stand.getEquipment().setHelmet(createSkullStatic(skullTexture));
        return stand;
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
        Player receiver = Bukkit.getPlayer(receiverId);
        if (receiver == null) {
            return;
        }
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar("Drone", settings.bossbarColor(), org.bukkit.boss.BarStyle.SEGMENTED_10);
        }
        bossBar.addPlayer(receiver);
        bossBar.setVisible(true);
        Location refLoc = landed
                ? (landedLocation != null ? landedLocation : (stand != null ? stand.getLocation() : lastKnownLocation))
                : (stand != null ? stand.getLocation() : expectedLocation(Bukkit.getCurrentTick()));
        updateBossBar(manager, refLoc, Bukkit.getCurrentTick());
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

        Player receiver = Bukkit.getPlayer(receiverId);
        if (receiver == null || bossBar == null || droneRefLocation.getWorld() == null) {
            return;
        }
        Location receiverLoc = receiver.getLocation();
        int distanceM;
        if (receiverLoc.getWorld().equals(droneRefLocation.getWorld())) {
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
        bossBar.setTitle(manager.renderBossbar(distanceM, eta));
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
        if (stand != null && !stand.isDead()) {
            if (!stand.getWorld().equals(preferredLocation.getWorld())) {
                if (isChunkLoaded(preferredLocation)) {
                    stand.teleport(preferredLocation);
                } else {
                    parkStandUntilChunkLoads(manager);
                }
            }
            if (stand != null && !stand.isDead()) {
                standId = stand.getUniqueId();
                return;
            }
        }
        if (!isChunkLoaded(preferredLocation)) {
            return;
        }
        ArmorStand respawned = spawnDroneEntity(preferredLocation, settings.skullTexture());
        if (respawned == null) {
            return;
        }
        UUID previous = standId;
        this.stand = respawned;
        this.standId = respawned.getUniqueId();
        this.lastKnownLocation = preferredLocation.clone();
        this.standParked = false;
        if (landed) {
            stand.setGlowing(true);
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
}
