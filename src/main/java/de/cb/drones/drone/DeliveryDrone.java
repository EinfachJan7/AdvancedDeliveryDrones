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
    private final List<UUID> attachedAnimalIds;
    private final ArmorStand stand;
    private final Deque<Location> particleTrail = new LinkedList<>();
    private ArmorStand hologramStand;

    private DroneSettings settings;
    private boolean landed;
    private boolean landingNotified;
    private Location pendingLanding;
    private Location landedLocation;
    private boolean openedByReceiver;
    private long lastInteractionTick;
    private BukkitTask ticker;
    private BukkitTask beaconTicker;
    private BossBar bossBar;

    public DeliveryDrone(
            UUID droneId,
            UUID senderId,
            UUID receiverId,
            String receiverName,
            Location fixedTarget,
            Inventory inventory,
            List<UUID> attachedAnimalIds,
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
        this.attachedAnimalIds = attachedAnimalIds == null ? List.of() : List.copyOf(attachedAnimalIds);
        this.settings = settings;
        this.stand = stand;
        this.lastInteractionTick = createdTick;
    }

    public UUID droneId() {
        return droneId;
    }

    public UUID standId() {
        return stand.getUniqueId();
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

    public Inventory inventory() {
        return inventory;
    }

    public Location currentLocation() {
        return stand.getLocation().clone();
    }

    public boolean isLanded() {
        return landed;
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
            if (animal.isDead() || !animal.isLeashed()) {
                continue;
            }
            Entity holder = animal.getLeashHolder();
            if (holder != null && holder.getUniqueId().equals(stand.getUniqueId())) {
                animal.setLeashHolder(null);
            }
        }
    }

    public void applySettings(DroneSettings settings, DroneManager manager) {
        this.settings = settings;
        stand.getEquipment().setHelmet(createSkull(settings.skullTexture()));
        if (landed) {
            stand.setGlowing(true);
            stand.setCustomNameVisible(settings.hologramEnabled());
            if (settings.hologramEnabled()) {
                updateHologram(Bukkit.getCurrentTick(), manager);
            }
        }
    }

    public void startFlight(DroneManager manager) {
        applySettings(settings, manager);
        initBossbar(manager);
        this.ticker = Bukkit.getScheduler().runTaskTimer(manager.plugin(), () -> tickFlight(manager), 1L, 1L);
    }

    private void tickFlight(DroneManager manager) {
        if (fixedTarget.getWorld() == null || !fixedTarget.getWorld().equals(stand.getWorld())) {
            return;
        }

        long nowTick = Bukkit.getCurrentTick();
        Location expected = expectedLocation(nowTick);
        Location bossbarRef = landed ? (landedLocation != null ? landedLocation : stand.getLocation()) : expected;

        if (!landed && expected.distanceSquared(fixedTarget) <= settings.deliveryRadius() * settings.deliveryRadius()) {
            if (pendingLanding == null) {
                pendingLanding = expected.clone();
            }
            if (isChunkLoaded(pendingLanding)) {
                landAt(manager, computeLandingFrom(pendingLanding));
                pendingLanding = null;
                if (!landingNotified) {
                    landingNotified = true;
                    Player receiver = Bukkit.getPlayer(receiverId);
                    if (receiver != null && receiver.isOnline()) {
                        receiver.sendMessage(manager.message("landing-notif", "<radius>", String.valueOf((int) settings.deliveryRadius())));
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
            }
        }

        if (!landed && isChunkLoaded(expected)) {
            stand.teleport(expected);
            tickAttachedAnimalFollow(expected);
            updateParticleTrail();
            stand.getWorld().playSound(stand.getLocation(), settings.flightSound(), 0.05f, 1.3f);
        }
        if (Bukkit.getCurrentTick() % 20L == 0L) {
            if (landed && isChunkLoaded(stand.getLocation())) {
                updateHologram(Bukkit.getCurrentTick(), manager);
            }
            updateBossBar(manager, bossbarRef);
        }
    }

    private void landAt(DroneManager manager, Location landing) {
        World world = landing.getWorld();
        if (world == null) {
            return;
        }
        stand.teleport(landing);
        this.landedLocation = landing.clone();
        stand.setGlowing(true);
        this.landed = true;
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
        Location pos = stand.getLocation().clone().add(0.0, settings.hologramOffset(), 0.0);
        hologramStand.teleport(pos);
        hologramStand.customName(manager.renderHologram(this, currentTick));
        hologramStand.setCustomNameVisible(true);
    }

    private void renderReceiverBeacon(Player receiver) {
        if (!receiver.isOnline() || !landed) {
            return;
        }
        Location loc = stand.getLocation().clone().add(0, 0.4, 0);
        for (DroneSettings.ParticleEffect effect : settings.particles()) {
            if (effect.data() != null) {
                receiver.spawnParticle(effect.particle(), loc, 15, 0.2, 6, 0.2, 0.0, effect.data());
            } else {
                receiver.spawnParticle(effect.particle(), loc, 15, 0.2, 6, 0.2, 0.0);
            }
        }
    }

    private void updateParticleTrail() {
        Location now = stand.getLocation().clone().add(0.0, settings.particleYOffset(), 0.0);
        particleTrail.addFirst(now);
        while (particleTrail.size() > settings.particleTrailLength()) {
            particleTrail.removeLast();
        }

        int index = 0;
        for (Location point : particleTrail) {
            int count = Math.max(1, settings.particleCount() - (index / 3));
            double spread = 0.02 + (index * 0.01);
            for (DroneSettings.ParticleEffect effect : settings.particles()) {
                if (effect.data() != null) {
                    point.getWorld().spawnParticle(effect.particle(), point, count, spread, spread, spread, 0.0, effect.data());
                } else {
                    point.getWorld().spawnParticle(effect.particle(), point, count, spread, spread, spread, 0.0);
                }
            }
            index++;
        }
    }

    private void tickAttachedAnimalFollow(Location expectedDroneLocation) {
        List<LivingEntity> animals = attachedAnimals();
        if (animals.isEmpty()) {
            return;
        }
        Location target = expectedDroneLocation.clone().add(0.0, -0.2, 0.0);
        for (LivingEntity animal : animals) {
            if (animal.isDead()) {
                continue;
            }
            if (!animal.getWorld().equals(stand.getWorld())) {
                continue;
            }
            if (animal.getLocation().distanceSquared(target) > 16.0D && isChunkLoaded(target)) {
                animal.teleport(target);
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
        if (!stand.isDead()) {
            stand.remove();
        }
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
            bossBar = Bukkit.createBossBar("Drone", org.bukkit.boss.BarColor.YELLOW, org.bukkit.boss.BarStyle.SEGMENTED_10);
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
        Player receiver = Bukkit.getPlayer(receiverId);
        if (receiver == null || bossBar == null) {
            return;
        }
        double distance = receiver.getLocation().distance(droneRefLocation);
        bossBar.setProgress(1.0D);
        bossBar.setTitle(manager.renderBossbar(distance, etaSeconds(Bukkit.getCurrentTick())));
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
        if (attachedAnimalIds.isEmpty()) {
            return List.of();
        }
        List<LivingEntity> result = new ArrayList<>();
        for (UUID attachedAnimalId : attachedAnimalIds) {
            Entity entity = Bukkit.getEntity(attachedAnimalId);
            if (entity instanceof LivingEntity living) {
                result.add(living);
            }
        }
        return result;
    }
}
