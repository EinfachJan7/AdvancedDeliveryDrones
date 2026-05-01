package de.cb.drones.performance;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PerformanceOptimizer {
    private final AdvancedDeliveryDronesPlugin plugin;
    private final Set<Location> chunkLoadQueue = ConcurrentHashMap.newKeySet();
    private final Set<Location> recentlyLoadedChunks = new HashSet<>();
    private BukkitTask cleanupTask;
    private final long CHUNK_COOLDOWN = 100L; // 5 seconds in ticks
    private final Map<String, Long> lastParticleSpawn = new ConcurrentHashMap<>();
    private final long PARTICLE_COOLDOWN = 5L; // 0.25 seconds in ticks
    private final Set<UUID> activeDrones = ConcurrentHashMap.newKeySet();

    public PerformanceOptimizer(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * Optimized chunk loading with cooldown to prevent excessive loading
     */
    public CompletableFuture<Boolean> loadChunkOptimized(Location location) {
        if (location == null || location.getWorld() == null) {
            return CompletableFuture.completedFuture(false);
        }

        long currentTick = plugin.getServer().getCurrentTick();
        String chunkKey = getChunkKey(location);
        
        // Check if recently loaded (within cooldown)
        if (recentlyLoadedChunks.contains(location)) {
            return CompletableFuture.completedFuture(true);
        }

        // Check if already loaded
        if (isChunkLoaded(location)) {
            recentlyLoadedChunks.add(location);
            return CompletableFuture.completedFuture(true);
        }

        // Add to queue for batch processing
        chunkLoadQueue.add(location);
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Load chunk synchronously but with limits
                World world = location.getWorld();
                int chunkX = location.getBlockX() >> 4;
                int chunkZ = location.getBlockZ() >> 4;
                
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    recentlyLoadedChunks.add(location);
                    return true;
                }
                return false;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load chunk at " + location);
                return false;
            }
        });
    }

    /**
     * Batch process chunk loading to reduce overhead
     */
    private void processChunkQueue() {
        if (chunkLoadQueue.isEmpty()) {
            return;
        }

        // Process up to 5 chunks per tick to prevent lag
        int processed = 0;
        Set<Location> toProcess = new HashSet<>(chunkLoadQueue);
        chunkLoadQueue.clear();

        for (Location location : toProcess) {
            if (processed >= 5) {
                // Add remaining back to queue
                chunkLoadQueue.addAll(toProcess.stream().skip(processed).toList());
                break;
            }

            if (!isChunkLoaded(location)) {
                World world = location.getWorld();
                int chunkX = location.getBlockX() >> 4;
                int chunkZ = location.getBlockZ() >> 4;
                
                try {
                    world.getChunkAt(chunkX, chunkZ);
                    recentlyLoadedChunks.add(location);
                    processed++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load chunk in batch: " + location);
                }
            } else {
                recentlyLoadedChunks.add(location);
            }
        }
    }

    /**
     * Optimized particle spawning with distance culling
     */
    public void spawnParticlesOptimized(Location location, org.bukkit.Particle particle, int count, 
                                       double offsetX, double offsetY, double offsetZ, double extra) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        // Skip if no players are nearby (within 64 blocks)
        if (!hasNearbyPlayers(location, 64)) {
            return;
        }

        // Reduce particle count based on distance to nearest player
        double nearestDistance = getNearestPlayerDistance(location);
        if (nearestDistance > 24) {
            count = Math.max(1, count / 3); // Third particles at medium distance
        }
        if (nearestDistance > 40) {
            count = 1; // Minimal particles at far distance
        }
        if (nearestDistance > 56) {
            return; // Skip particles at very far distance
        }

        // Further reduce if performance throttling is active
        if (shouldThrottlePerformance()) {
            count = Math.max(1, count / 2);
        }

        location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
    }

    /**
     * Optimized particle spawning with data
     */
    public void spawnParticlesOptimized(Location location, org.bukkit.Particle particle, int count,
                                       double offsetX, double offsetY, double offsetZ, double extra, Object data) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        // Skip if no players are nearby
        if (!hasNearbyPlayers(location, 64)) {
            return;
        }

        // Reduce particle count based on distance
        double nearestDistance = getNearestPlayerDistance(location);
        if (nearestDistance > 32) {
            count = Math.max(1, count / 2);
        }
        if (nearestDistance > 48) {
            count = 1;
        }

        location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
    }

    private boolean hasNearbyPlayers(Location location, double radius) {
        return location.getWorld().getNearbyEntities(location, radius, radius, radius).stream()
                .anyMatch(entity -> entity instanceof org.bukkit.entity.Player);
    }

    private double getNearestPlayerDistance(Location location) {
        return location.getWorld().getPlayers().stream()
                .mapToDouble(player -> player.getLocation().distance(location))
                .min()
                .orElse(Double.MAX_VALUE);
    }

    private boolean isChunkLoaded(Location location) {
        World world = location.getWorld();
        if (world == null) return false;
        
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    private String getChunkKey(Location location) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return location.getWorld().getName() + "," + chunkX + "," + chunkZ;
    }

    private void startCleanupTask() {
        this.cleanupTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // Process chunk queue asynchronously to avoid blocking main thread
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::processChunkQueue);
            
            // Clean up recently loaded chunks (remove old entries)
            recentlyLoadedChunks.removeIf(loc -> {
                // Simple cleanup - in practice you'd want to track timestamps
                return false; // Keep for now, will be cleaned by size limit
            });
            
            // Limit size of recently loaded chunks to prevent memory leak
            if (recentlyLoadedChunks.size() > 100) {
                recentlyLoadedChunks.clear();
            }
        }, 100L, 100L); // Every 5 seconds instead of every second
    }

    /**
     * Register a drone for performance tracking
     */
    public void registerDrone(UUID droneId) {
        activeDrones.add(droneId);
    }
    
    /**
     * Unregister a drone from performance tracking
     */
    public void unregisterDrone(UUID droneId) {
        activeDrones.remove(droneId);
        lastParticleSpawn.remove(droneId.toString());
    }
    
    /**
     * Check if particle should be spawned based on cooldown
     */
    public boolean shouldSpawnParticles(UUID droneId) {
        String key = droneId.toString();
        long currentTick = Bukkit.getCurrentTick();
        Long lastSpawn = lastParticleSpawn.get(key);
        
        if (lastSpawn == null || (currentTick - lastSpawn) >= PARTICLE_COOLDOWN) {
            lastParticleSpawn.put(key, currentTick);
            return true;
        }
        return false;
    }
    
    /**
     * Get the number of active drones for performance monitoring
     */
    public int getActiveDroneCount() {
        return activeDrones.size();
    }
    
    /**
     * Check if performance should be throttled based on drone count
     */
    public boolean shouldThrottlePerformance() {
        return activeDrones.size() > 50; // Throttle if more than 50 drones
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        chunkLoadQueue.clear();
        recentlyLoadedChunks.clear();
        lastParticleSpawn.clear();
        activeDrones.clear();
    }
}
