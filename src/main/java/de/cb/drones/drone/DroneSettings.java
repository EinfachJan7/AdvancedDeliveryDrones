package de.cb.drones.drone;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

public record DroneSettings(
        double speed,
        double startupSpeed,
        int startupSeconds,
        double deliveryRadius,
        long despawnTicks,
        DespawnMode despawnMode,
        List<ParticleEffect> particles,
        int particleCount,
        int particleTrailLength,
        double particleYOffset,
        Sound flightSound,
        int inventorySize,
        String skullTexture,
        int maxActivePerSender,
        List<String> blockedWorlds,
        boolean hologramEnabled,
        String hologramFormat,
        double hologramOffset,
        boolean bossbarEnabled,
        String bossbarFormat
) {
    public static DroneSettings fromConfig(FileConfiguration cfg) {
        String section = "settings.drone.";
        double speed = cfg.getDouble(section + "speed", 0.3D);
        double startupSpeed = Math.max(0.01D, cfg.getDouble(section + "startup-speed", Math.min(0.2D, speed)));
        int startupSeconds = Math.max(0, cfg.getInt(section + "startup-seconds", 3));
        double deliveryRadius = cfg.getDouble(section + "delivery-radius", 50.0D);
        long despawnTicks = Math.max(1L, cfg.getLong(section + "despawn-time-minutes", 10L) * 60L * 20L);
        DespawnMode despawnMode = DespawnMode.fromName(cfg.getString(section + "despawn-mode", "COLLECT"));
        List<ParticleEffect> particles = parseParticles(cfg, section);
        int particleCount = Math.max(1, cfg.getInt(section + "particle-count", 3));
        int particleTrailLength = Math.max(2, cfg.getInt(section + "particle-trail-length", 10));
        double particleYOffset = cfg.getDouble(section + "particle-y-offset", 1.0D);
        Sound sound = parseSound(cfg.getString(section + "flight-sound", "entity.elytra.flying"));
        int size = normalizeSize(cfg.getInt(section + "inventory-size", 54));
        String texture = cfg.getString(section + "skull-texture", "");
        int maxActive = Math.max(1, cfg.getInt(section + "max-active-per-sender", 3));
        List<String> blockedWorlds = cfg.getStringList(section + "blocked-worlds").stream()
                .map(String::toLowerCase)
                .toList();
        boolean hologramEnabled = cfg.getBoolean(section + "hologram.enabled", true);
        String hologramFormat = cfg.getString(section + "hologram.format", "<yellow>Paket fuer <white><receiver></white> <gray>(<minutes>m <seconds>s)</gray>");
        double hologramOffset = cfg.getDouble(section + "hologram.offset-y", 1.0D);
        boolean bossbarEnabled = cfg.getBoolean(section + "bossbar.enabled", true);
        String bossbarFormat = cfg.getString(section + "bossbar.format", "<gold>Distanz: <white><distance>m</white> <gray>| ETA: <white><eta>s</white></gray>");
        return new DroneSettings(
                speed,
                startupSpeed,
                startupSeconds,
                deliveryRadius,
                despawnTicks,
                despawnMode,
                particles,
                particleCount,
                particleTrailLength,
                particleYOffset,
                sound,
                size,
                texture,
                maxActive,
                blockedWorlds,
                hologramEnabled,
                hologramFormat,
                hologramOffset,
                bossbarEnabled,
                bossbarFormat
        );
    }

    private static Particle parseParticle(String value) {
        try {
            return Particle.valueOf(value.toUpperCase());
        } catch (Exception ignored) {
            return Particle.ELECTRIC_SPARK;
        }
    }

    private static List<ParticleEffect> parseParticles(FileConfiguration cfg, String section) {
        List<String> raw = cfg.getStringList(section + "particle-types");
        if (raw.isEmpty()) {
            raw = List.of("ELECTRIC_SPARK");
        }
        List<ParticleEffect> particles = new ArrayList<>();
        for (String entry : raw) {
            ParticleEffect effect = parseParticleEffect(entry);
            if (effect != null) {
                particles.add(effect);
            }
        }
        if (particles.isEmpty()) {
            particles.add(new ParticleEffect(Particle.ELECTRIC_SPARK, null));
        }
        return List.copyOf(particles);
    }

    private static ParticleEffect parseParticleEffect(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        // Format: DUST:r,g,b[:size]
        if (value.toUpperCase().startsWith("DUST:")) {
            try {
                String[] parts = value.substring("DUST:".length()).split(":");
                String[] rgb = parts[0].split(",");
                int r = Integer.parseInt(rgb[0].trim());
                int g = Integer.parseInt(rgb[1].trim());
                int b = Integer.parseInt(rgb[2].trim());
                float size = parts.length >= 2 ? Float.parseFloat(parts[1].trim()) : 1.0f;
                Particle.DustOptions options = new Particle.DustOptions(Color.fromRGB(r, g, b), size);
                return new ParticleEffect(Particle.DUST, options);
            } catch (Exception ignored) {
                return new ParticleEffect(Particle.DUST, new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.0f));
            }
        }
        return new ParticleEffect(parseParticle(value), null);
    }

    private static Sound parseSound(String value) {
        try {
            return Sound.valueOf(value.toUpperCase().replace('.', '_'));
        } catch (Exception ignored) {
            return Sound.ITEM_ELYTRA_FLYING;
        }
    }

    private static int normalizeSize(int requested) {
        int clamped = Math.min(54, Math.max(9, requested));
        return clamped - (clamped % 9);
    }

    public enum DespawnMode {
        DELETE,
        COLLECT;

        public static DespawnMode fromName(String value) {
            for (DespawnMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return COLLECT;
        }
    }

    public record ParticleEffect(Particle particle, Object data) {
    }
}
