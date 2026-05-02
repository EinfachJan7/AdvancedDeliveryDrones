package de.cb.drones.drone;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
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
    private final long flightStartTick;
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
    private static final long BOSSBAR_UPDATE_INTERVAL = 5L; // Update every 5 ticks (0.25 seconds)

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
        if (stand != null && !stand.isDead()) {
            stand.getEquipment().setHelmet(createSkull(settings.skullTexture()));
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
        applySettings(settings, manager);
        initBossbar(manager);
        
        // Register with performance optimizer
        if (droneManager != null && droneManager.getPerformanceOptimizer() != null) {
            droneManager.getPerformanceOptimizer().registerDrone(droneId);
        }
        
        this.ticker = Bukkit.getScheduler().runTaskTimer(manager.plugin(), () -> tickFlight(manager), 1L, 1L);
    }

    private void tickFlight(DroneManager manager) {
        if (fixedTarget.getWorld() == null) {
            return;
        }

        long nowTick = Bukkit.getCurrentTick();
        Location expected = expectedLocation(nowTick);
        // Keep virtual position progressing even when chunks are unloaded.
        lastKnownLocation = expected.clone();
        preloadTargetChunkIfNeeded(expected);
        Location standAnchor = landed
                ? computeLandingFrom(landedLocation != null ? landedLocation : fixedTarget)
                : expected;
        ensureStandPresent(manager, standAnchor);
        boolean standAvailable = stand != null && !stand.isDead();
        if (standAvailable && !fixedTarget.getWorld().equals(stand.getWorld())) {
            return;
        }
        Location bossbarRef = landed
                ? (landedLocation != null ? landedLocation : (standAvailable ? stand.getLocation() : lastKnownLocation))
                : expected;

        double deliveryRadius = exactSocketTarget ? 0.5 : settings.deliveryRadius();
        if (!landed && expected.distanceSquared(fixedTarget) <= deliveryRadius * deliveryRadius) {
            if (pendingLanding == null) {
                pendingLanding = fixedTarget.clone();
            }
            if (isChunkLoaded(pendingLanding)) {
                Location landingSpot = exactSocketTarget ? pendingLanding.clone() : computeLandingFrom(pendingLanding);
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
                                    receiver.sendMessage(manager.message("landing-notif-socket", "<socket>", socketName));
                                } else {
                                    receiver.sendMessage(manager.message("landing-notif", "<radius>", String.valueOf((int) settings.deliveryRadius())));
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
                        // Animate smooth descent
                        double progress = (double) elapsedTicks / smoothLandingDuration;
                        // Use ease-in-out cubic function for smooth animation
                        double easedProgress = progress < 0.5 
                            ? 4 * progress * progress * progress 
                            : 1 - Math.pow(-2 * progress + 2, 3) / 2;
                        
                        Vector startVec = smoothLandingStart.toVector();
                        Vector endVec = landingSpot.toVector();
                        Vector current = startVec.add(endVec.subtract(startVec).multiply(easedProgress));
                        
                        Location animatedPos = new Location(landingSpot.getWorld(), current.getX(), current.getY(), current.getZ());
                        ensureStandPresent(manager, animatedPos);
                        if (stand != null && !stand.isDead()) {
                            stand.teleport(animatedPos);
                            tickAttachedAnimalFollow(animatedPos);
                            updateParticleTrail();
                            stand.getWorld().playSound(stand.getLocation(), settings.flightSound(), 0.05f, 1.3f);
                        }
                    }
                }
            }
        }

        if (!landed) {
            if (!isChunkLoaded(expected)) {
                if (standAvailable) {
                    parkStandUntilChunkLoads(manager);
                    standAvailable = false;
                }
            } else if (standAvailable) {
                stand.teleport(expected);
                tickAttachedAnimalFollow(expected);
                updateParticleTrail();
                stand.getWorld().playSound(stand.getLocation(), settings.flightSound(), 0.05f, 1.3f);
            }
        }
        if (landed && standAvailable) {
            Location landedRef = landedLocation != null ? landedLocation : (stand != null && !stand.isDead() ? stand.getLocation() : lastKnownLocation);
            tickAttachedAnimalFollow(landedRef);
        }
        if (Bukkit.getCurrentTick() % 20L == 0L) {
            if (landed && standAvailable && isChunkLoaded(stand.getLocation())) {
                updateHologram(Bukkit.getCurrentTick(), manager);
            }
            updateBossBar(manager, bossbarRef);
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
        double scale = 1.0 - (easedProgress * 0.8); // Shrink to 20% size
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
        spawnTransportedAnimalsAtLanding();
        updateHologram(Bukkit.getCurrentTick(), manager);
        initBossbar(manager);
    }

    private Location computeLandingFrom(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return at;
        }
        int x = at.getBlockX();
        int z = at.getBlockZ();
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        y = Math.max(y, world.getMinHeight() + 1);
        return new Location(world, x + 0.5, y + 0.1, z + 0.5);
    }

    private Location expectedLocation(long nowTick) {
        Location target = fixedTarget.clone().add(0.0, 0.1, 0.0);
        Vector delta = target.toVector().subtract(startLocation.toVector());
        double distance = delta.length();
        if (distance <= 0.001D) {
            return target;
        }
        delta.normalize();
        double traveled = traveledDistance(nowTick);
        double factor = Math.min(1.0D, traveled / distance);
        Vector pos = startLocation.toVector().add(delta.multiply(distance * factor));
        return new Location(startLocation.getWorld(), pos.getX(), pos.getY(), pos.getZ(), target.getYaw(), target.getPitch());
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
        // Don't show hologram for socket drones
        if (exactSocketTarget) {
            if (hologramStand != null && !hologramStand.isDead()) {
                hologramStand.remove();
            }
            hologramStand = null;
            return;
        }
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
        
        Location now = stand.getLocation().clone().add(0.0, settings.particleYOffset(), 0.0);
        particleTrail.addFirst(now);
        while (particleTrail.size() > settings.particleTrailLength()) {
            particleTrail.removeLast();
        }

        int index = 0;
        for (Location point : particleTrail) {
            int count = Math.max(1, settings.particleCount() - (index / 3));
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
        Location target = expectedDroneLocation.clone().add(0.0, -0.2, 0.0);
        for (LivingEntity animal : animals) {
            if (animal.isDead()) {
                continue;
            }
            if (!animal.getWorld().equals(stand.getWorld())) {
                continue;
            }
            double toTargetSq = animal.getLocation().distanceSquared(target);
            boolean teleported = false;
            if (toTargetSq > 16.0D && isChunkLoaded(target)) {
                animal.teleport(target);
                teleported = true;
            }
            double toStandSq = animal.getLocation().distanceSquared(standLocation);
            // Avoid repeated re-leash attempts when the animal cannot safely catch up yet.
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
        stand.getEquipment().setHelmet(createSkull(skullTexture));
        return stand;
    }

    private static ItemStack createSkull(String texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Drone"));
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
        updateBossBar(manager, landed ? (landedLocation != null ? landedLocation : stand.getLocation()) : stand.getLocation());
    }

    private void updateBossBar(DroneManager manager, Location droneRefLocation) {
        if (!settings.bossbarEnabled()) {
            if (bossBar != null) {
                bossBar.removeAll();
                bossBar.setVisible(false);
            }
            return;
        }
        long currentTick = Bukkit.getCurrentTick();
        // Only update boss bar every BOSSBAR_UPDATE_INTERVAL ticks to reduce performance impact
        if (currentTick - lastBossBarUpdate < BOSSBAR_UPDATE_INTERVAL) {
            return;
        }
        lastBossBarUpdate = currentTick;
        
        Player receiver = Bukkit.getPlayer(receiverId);
        if (receiver == null || bossBar == null) {
            return;
        }
        double distance = receiver.getLocation().distance(droneRefLocation);
        bossBar.setProgress(1.0D);
        bossBar.setTitle(manager.renderBossbar(distance, etaSeconds(currentTick)));
    }

    private long etaSeconds(long nowTick) {
        if (landed) {
            return 0L;
        }
        Location target = fixedTarget.clone().add(0.0, 0.1, 0.0);
        Vector delta = target.toVector().subtract(startLocation.toVector());
        double totalDistance = delta.length();
        double traveled = traveledDistance(nowTick);
        double remainingToRadius = Math.max(0.0D, (totalDistance - traveled) - settings.deliveryRadius());
        return estimateEtaSeconds(remainingToRadius, nowTick);
    }

    private double traveledDistance(long nowTick) {
        long elapsedTicks = Math.max(0L, nowTick - flightStartTick);
        long startupTicks = settings.startupSeconds() * 20L;
        long startupPart = Math.min(elapsedTicks, startupTicks);
        long cruisePart = Math.max(0L, elapsedTicks - startupTicks);
        return (startupPart * settings.startupSpeed()) + (cruisePart * settings.speed());
    }

    private long estimateEtaSeconds(double remainingDistance, long nowTick) {
        if (remainingDistance <= 0.0D) {
            return 0L;
        }
        long elapsedTicks = Math.max(0L, nowTick - flightStartTick);
        long startupTicks = settings.startupSeconds() * 20L;
        long startupTicksLeft = Math.max(0L, startupTicks - elapsedTicks);
        double startupDistanceLeft = startupTicksLeft * settings.startupSpeed();

        double seconds = 0.0D;
        if (startupDistanceLeft > 0.0D) {
            if (remainingDistance <= startupDistanceLeft) {
                seconds += remainingDistance / (settings.startupSpeed() * 20.0D);
                return (long) Math.ceil(seconds);
            }
            seconds += startupTicksLeft / 20.0D;
            remainingDistance -= startupDistanceLeft;
        }

        double cruisePerSecond = settings.speed() * 20.0D;
        if (cruisePerSecond <= 0.0001D) {
            return Long.MAX_VALUE;
        }
        seconds += remainingDistance / cruisePerSecond;
        return (long) Math.ceil(seconds);
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
            standId = stand.getUniqueId();
            return;
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
        World world = fixedTarget.getWorld();
        if (world == null) {
            return;
        }
        double triggerDistance = Math.max(settings.deliveryRadius() * 4.0D, 64.0D);
        if (expected.distanceSquared(fixedTarget) > triggerDistance * triggerDistance) {
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
