package de.cb.drones.drone;

import de.cb.drones.gui.GuiConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

public record DroneSettings(
        double speed,
        double startupSpeed,
        int startupSeconds,
        double approachSpeed,
        double approachDistance,
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
        boolean carryLeashedAnimals,
        int maxLeashedAnimalsPerDrone,
        int maxSocketsPerPlayer,
        boolean socketsEnabled,
        boolean playersEnabled,
        List<String> blockedWorlds,
        boolean hologramEnabled,
        String hologramFormat,
        double hologramOffset,
        boolean bossbarEnabled,
        String bossbarFormat,
        org.bukkit.boss.BarColor bossbarColor,
        boolean collectionAnimationEnabled,
        GuiConfiguration guiConfig,
        boolean launchAnimationEnabled,
        int launchAnimationSeconds,
        Sound launchSound,
        float launchSoundVolume,
        boolean followGlidingPlayer,
        boolean locateParticlesEnabled,
        ParticleEffect locateParticle
) {
    public static DroneSettings fromConfig(FileConfiguration cfg, FileConfiguration guiConfig) {
        String section = "settings.drone.";
        double speed = cfg.getDouble(section + "speed", 0.3D);
        double startupSpeed = Math.max(0.01D, cfg.getDouble(section + "startup-speed", Math.min(0.2D, speed)));
        int startupSeconds = Math.max(0, cfg.getInt(section + "startup-seconds", 3));
        double approachSpeed = Math.max(0.01D, cfg.getDouble(section + "approach-speed", 0.2D));
        double approachDistance = Math.max(50.0D, cfg.getDouble(section + "approach-distance", 150.0D));
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
        int maxActive = Math.max(1, cfg.getInt(section + "max-active-per-sender", 1));
        boolean carryLeashedAnimals = cfg.getBoolean(section + "carry-leashed-animals", false);
        int maxLeashedAnimalsPerDrone = Math.max(0, cfg.getInt(section + "max-leashed-animals-per-drone", 1));
        int maxSocketsPerPlayer = Math.max(1, cfg.getInt(section + "max-sockets-per-player", 3));
        boolean socketsEnabled = cfg.getBoolean(section + "sockets-enabled", true);
        boolean playersEnabled = cfg.getBoolean(section + "players-enabled", true);
        List<String> blockedWorlds = cfg.getStringList(section + "blocked-worlds").stream()
                .map(String::toLowerCase)
                .toList();
        boolean hologramEnabled = cfg.getBoolean(section + "hologram.enabled", true);
        String hologramFormat = cfg.getString(section + "hologram.format", "<yellow>Paket fuer <white><receiver></white> <gray>(<minutes>m <seconds>s)</gray>");
        double hologramOffset = cfg.getDouble(section + "hologram.offset-y", 1.0D);
        boolean bossbarEnabled = cfg.getBoolean(section + "bossbar.enabled", true);
        String bossbarFormat = cfg.getString(section + "bossbar.format", "<gold>Distanz: <white><distance>m</white> <gray>| ETA: <white><eta>s</white></gray>");
        org.bukkit.boss.BarColor bossbarColor = parseBarColor(cfg.getString(section + "bossbar.color", "YELLOW"));
        boolean collectionAnimationEnabled = cfg.getBoolean(section + "collection-animation.enabled", true);
        boolean launchAnimationEnabled = cfg.getBoolean(section + "launch-animation.enabled", true);
        int launchAnimationSeconds = Math.max(1, cfg.getInt(section + "launch-animation.seconds", 3));
        Sound launchSound = parseSound(cfg.getString(section + "launch-animation.sound", "entity.firework_rocket.launch"));
        float launchSoundVolume = (float) cfg.getDouble(section + "launch-animation.sound-volume", 1.0D);
        boolean followGlidingPlayer = cfg.getBoolean(section + "follow-gliding-player", true);
        boolean locateParticlesEnabled = cfg.getBoolean(section + "locate-particles.enabled", true);
        ParticleEffect locateParticle = parseParticleEffect(cfg.getString(section + "locate-particles.particle", "HAPPY_VILLAGER"));

        // Create GUI configuration from separate file
        GuiConfiguration guiConfigObj = new GuiConfiguration(guiConfig);

        return new DroneSettings(
                speed,
                startupSpeed,
                startupSeconds,
                approachSpeed,
                approachDistance,
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
                carryLeashedAnimals,
                maxLeashedAnimalsPerDrone,
                maxSocketsPerPlayer,
                socketsEnabled,
                playersEnabled,
                blockedWorlds,
                hologramEnabled,
                hologramFormat,
                hologramOffset,
                bossbarEnabled,
                bossbarFormat,
                bossbarColor,
                collectionAnimationEnabled,
                guiConfigObj,
                launchAnimationEnabled,
                launchAnimationSeconds,
                launchSound,
                launchSoundVolume,
                followGlidingPlayer,
                locateParticlesEnabled,
                locateParticle
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

    private static Material parseMaterial(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(value.trim(), true);
        return parsed == null ? fallback : parsed;
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

    private static GuiSettings parseGuiSettings(FileConfiguration cfg, String section, Map<String, GuiItem> defaultItems) {
        String title = cfg.getString(section + "title", "GUI");
        int size = Math.max(9, Math.min(54, cfg.getInt(section + "size", 27)));
        
        Map<String, GuiItem> items = new HashMap<>();
        for (Map.Entry<String, GuiItem> entry : defaultItems.entrySet()) {
            String key = entry.getKey();
            GuiItem defaultItem = entry.getValue();
            
            String itemSection = section + "items." + key;
            int position = cfg.getInt(itemSection + ".position", defaultItem.position());
            Material material = parseMaterial(cfg.getString(itemSection + ".material", defaultItem.material().name()), defaultItem.material());
            String name = cfg.getString(itemSection + ".name", defaultItem.name());
            List<String> lore = cfg.getStringList(itemSection + ".lore");
            if (lore.isEmpty()) {
                lore = defaultItem.lore();
            }
            
            items.put(key, new GuiItem(position, material, name, lore));
        }
        
        // Parse fill item
        String fillSection = section + "fill-item";
        Material fillMaterial = parseMaterial(cfg.getString(fillSection + ".material", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
        String fillName = cfg.getString(fillSection + ".name", " ");
        GuiItem fillItem = new GuiItem(-1, fillMaterial, fillName, List.of());
        
        return new GuiSettings(title, size, items, fillItem);
    }

    private static org.bukkit.boss.BarColor parseBarColor(String value) {
        if (value == null || value.isBlank()) {
            return org.bukkit.boss.BarColor.YELLOW;
        }
        try {
            return org.bukkit.boss.BarColor.valueOf(value.toUpperCase());
        } catch (Exception ignored) {
            return org.bukkit.boss.BarColor.YELLOW;
        }
    }

    private static Map<String, GuiItem> createDefaultMainMenuItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("send", new GuiItem(11, Material.PLAYER_HEAD, "<green>Drohne senden", List.of("<gray>Wähle einen Spieler aus", "<gray>um ihm eine Drohne zu senden")));
        items.put("toggle", new GuiItem(13, Material.REDSTONE, "<yellow>Drohnen-Empfang umschalten", List.of("<gray>Schalte ein/aus ob du", "<gray>Drohnen empfangen möchtest")));
        items.put("decline", new GuiItem(15, Material.BARRIER, "<red>Eingehende Drohnen ablehnen", List.of("<gray>Lehne alle eingehenden", "<gray>Drohnen für dich ab")));
        items.put("preview", new GuiItem(22, Material.ENDER_EYE, "<aqua>Drohne-Vorschau", List.of("<gray>Zeige eine Vorschau deiner", "<gray>aktiven Drohnen")));
        return items;
    }

    private static Map<String, GuiItem> createDefaultPlayerSelectionItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("back", new GuiItem(45, Material.ARROW, "<yellow>Zurück", List.of("<gray>Zurück zum Hauptmenü")));
        return items;
    }

    private static Map<String, GuiItem> createDefaultTargetSelectionItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("back", new GuiItem(26, Material.ARROW, "<yellow>Zurück", List.of("<gray>Zurück zum Hauptmenü")));
        items.put("player", new GuiItem(11, Material.PLAYER_HEAD, "<green>Spieler auswählen", List.of("<gray>Wähle einen Spieler aus", "<gray>um ihm eine Drohne zu senden")));
        items.put("socket", new GuiItem(15, Material.BEACON, "<yellow>Socket auswählen", List.of("<gray>Wähle einen Socket aus", "<gray>um dort eine Drohne zu senden")));
        return items;
    }

    private static Map<String, GuiItem> createDefaultSocketSelectionItems() {
        Map<String, GuiItem> items = new HashMap<>();
        items.put("back", new GuiItem(45, Material.ARROW, "<yellow>Zurück", List.of("<gray>Zurück zur Zielauswahl")));
        return items;
    }
}
