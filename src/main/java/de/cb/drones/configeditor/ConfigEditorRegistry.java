package de.cb.drones.configeditor;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ConfigEditorRegistry {
    private static final List<ConfigCategory> CATEGORIES = List.of(
            new ConfigCategory("general", Material.COMPASS, "Allgemein"),
            new ConfigCategory("flight", Material.FEATHER, "Flug"),
            new ConfigCategory("gameplay", Material.DIAMOND, "Gameplay"),
            new ConfigCategory("visual", Material.AMETHYST_SHARD, "Darstellung"),
            new ConfigCategory("animations", Material.FIREWORK_ROCKET, "Animationen"),
            new ConfigCategory("containers", Material.CHEST, "Container"),
            new ConfigCategory("discord", Material.PAPER, "Discord")
    );

    private static final Map<String, ConfigOption> OPTIONS_BY_ID = new LinkedHashMap<>();
    private static final Map<String, List<ConfigOption>> OPTIONS_BY_CATEGORY = new LinkedHashMap<>();

    static {
        registerEnum("language", "language", "general", Material.BOOK,
                "Sprache", "Sprachauswahl", "de_DE", "en_EN", "es_ES", "fr_FR", "ru_RU", "zh_CN");
        register("plugin-check-updates", "plugin.check-updates", ConfigOptionType.BOOLEAN, "general", Material.LEVER,
                "Update-Check", "Modrinth-Updates beim Start prüfen");
        register("config-editor-messages", "plugin.config-editor-messages-enabled", ConfigOptionType.BOOLEAN, "general", Material.WRITTEN_BOOK,
                "Editor Messages", "Show notifications when config settings change");

        register("speed", "settings.drone.speed", ConfigOptionType.DOUBLE, "flight", Material.SUGAR,
                "Fluggeschwindigkeit", "Geschwindigkeit der Drohne");
        register("startup-speed", "settings.drone.startup-speed", ConfigOptionType.DOUBLE, "flight", Material.SUGAR,
                "Startgeschwindigkeit", "Geschwindigkeit beim Start");
        register("startup-seconds", "settings.drone.startup-seconds", ConfigOptionType.INT, "flight", Material.CLOCK,
                "Startdauer", "Startphase in Sekunden");
        register("approach-speed", "settings.drone.approach-speed", ConfigOptionType.DOUBLE, "flight", Material.SUGAR,
                "Anfluggeschwindigkeit", "Geschwindigkeit beim Anflug");
        register("approach-distance", "settings.drone.approach-distance", ConfigOptionType.DOUBLE, "flight", Material.ENDER_PEARL,
                "Anflugdistanz", "Abstand ab dem langsamer angeflogen wird");
        register("delivery-radius", "settings.drone.delivery-radius", ConfigOptionType.DOUBLE, "flight", Material.TARGET,
                "Lieferradius", "Radius für Zustellung");
        register("follow-gliding-player", "settings.drone.follow-gliding-player", ConfigOptionType.BOOLEAN, "flight", Material.ELYTRA,
                "Elytra folgen", "Drohne folgt gleitenden Spielern");
        register("follow-airborne-player", "settings.drone.follow-airborne-player-before-landing", ConfigOptionType.BOOLEAN, "flight", Material.FEATHER,
                "Luftfolge", "Folgt Empfängern in der Luft vor Landung");
        register("airborne-follow-min-height", "settings.drone.airborne-follow-min-height", ConfigOptionType.DOUBLE, "flight", Material.LADDER,
                "Min. Flughöhe", "Mindesthöhe für Luftfolge");
        register("airborne-follow-max-seconds", "settings.drone.airborne-follow-max-seconds-after-start", ConfigOptionType.INT, "flight", Material.CLOCK,
                "Max. Luftfolge-Zeit", "Sekunden nach Start für Luftfolge");
        register("flight-sound", "settings.drone.flight-sound", ConfigOptionType.STRING, "flight", Material.NOTE_BLOCK,
                "Flug-Sound", "Sound während des Flugs");

        register("despawn-time-minutes", "settings.drone.despawn-time-minutes", ConfigOptionType.INT, "gameplay", Material.CLOCK,
                "Despawn-Zeit", "Minuten bis Despawn");
        registerEnum("despawn-mode", "settings.drone.despawn-mode", "gameplay", Material.BARRIER,
                "Despawn-Modus", "DELETE oder COLLECT", "DELETE", "COLLECT");
        register("max-active-per-sender", "settings.drone.max-active-per-sender", ConfigOptionType.INT, "gameplay", Material.ARMOR_STAND,
                "Max. aktive Drohnen", "Pro Absender gleichzeitig");
        register("max-sockets-per-player", "settings.drone.max-sockets-per-player", ConfigOptionType.INT, "gameplay", Material.BEACON,
                "Max. Sockets", "Sockets pro Spieler");
        register("carry-leashed-animals", "settings.drone.carry-leashed-animals", ConfigOptionType.BOOLEAN, "gameplay", Material.LEAD,
                "Tiere transportieren", "Angeleinte Tiere mitnehmen");
        register("max-leashed-animals", "settings.drone.max-leashed-animals-per-drone", ConfigOptionType.INT, "gameplay", Material.LEAD,
                "Max. Tiere", "Tiere pro Drohne");
        register("animal-return-mode", "settings.drone.animal-return-mode", ConfigOptionType.STRING, "gameplay", Material.LEAD,
                "Tier-Rückkehr", "FLY oder TELEPORT bei Abbruch");
        register("open-inventory-on-send", "settings.drone.open-inventory-on-send", ConfigOptionType.BOOLEAN, "gameplay", Material.CHEST,
                "Inventar direkt öffnen", "true = Paket beim Senden direkt; false = Compose-Hub");
        register("send-cooldown-player", "settings.drone.send-cooldown-seconds-player", ConfigOptionType.INT, "gameplay", Material.CLOCK,
                "Cooldown Spieler", "Sekunden zwischen Spieler-Sends");
        register("send-cooldown-socket", "settings.drone.send-cooldown-seconds-socket", ConfigOptionType.INT, "gameplay", Material.CLOCK,
                "Cooldown Socket", "Sekunden zwischen Socket-Sends");
        register("allow-send-to-self-player", "settings.drone.allow-send-to-self-player", ConfigOptionType.BOOLEAN, "gameplay", Material.PLAYER_HEAD,
                "Selbst-Send Spieler", "Senden an sich selbst erlauben");
        register("allow-send-to-self-socket", "settings.drone.allow-send-to-self-socket", ConfigOptionType.BOOLEAN, "gameplay", Material.BEACON,
                "Selbst-Send Socket", "Senden an eigene Sockets erlauben");
        register("sockets-enabled", "settings.drone.sockets-enabled", ConfigOptionType.BOOLEAN, "gameplay", Material.BEACON,
                "Sockets aktiv", "Lieferstationen ein/aus");
        register("players-enabled", "settings.drone.players-enabled", ConfigOptionType.BOOLEAN, "gameplay", Material.PLAYER_HEAD,
                "Spieler-Ziele aktiv", "Spieler als Ziel ein/aus");
        register("inventory-size", "settings.drone.inventory-size", ConfigOptionType.INT, "gameplay", Material.CHEST,
                "Inventargröße", "Paket-Inventar (9–54)");

        register("skull-texture", "settings.drone.skull-texture", ConfigOptionType.STRING, "visual", Material.PLAYER_HEAD,
                "Kopf-Textur", "Base64 Skull-Textur");
        registerEnum("custom-model-provider", "settings.drone.custom-model.provider", "visual", Material.ITEM_FRAME,
                "Custom-Model Provider", "NONE, NEXO, ORAXEN, ITEMSADDER", "NONE", "NEXO", "ORAXEN", "ITEMSADDER");
        register("custom-model-item-id", "settings.drone.custom-model.item-id", ConfigOptionType.STRING, "visual", Material.ITEM_FRAME,
                "Custom-Model ID", "Item-ID beim Provider");
        register("glowing-enabled", "settings.drone.glowing-enabled", ConfigOptionType.BOOLEAN, "visual", Material.GLOWSTONE_DUST,
                "Leuchten", "Drohne leuchtet");
        register("particle-count", "settings.drone.particle-count", ConfigOptionType.INT, "visual", Material.BLAZE_POWDER,
                "Partikel-Anzahl", "Partikel pro Tick");
        register("particle-trail-length", "settings.drone.particle-trail-length", ConfigOptionType.INT, "visual", Material.STRING,
                "Partikel-Spur", "Länge der Partikelspur");
        register("particle-y-offset", "settings.drone.particle-y-offset", ConfigOptionType.DOUBLE, "visual", Material.SCAFFOLDING,
                "Partikel Y-Offset", "Vertikaler Offset");
        register("hologram-enabled", "settings.drone.hologram.enabled", ConfigOptionType.BOOLEAN, "visual", Material.NAME_TAG,
                "Hologramm", "Hologramm an/aus");
        register("hologram-offset-y", "settings.drone.hologram.offset-y", ConfigOptionType.DOUBLE, "visual", Material.ARMOR_STAND,
                "Hologramm Offset", "Y-Offset des Hologramms");
        register("hologram-format", "settings.drone.hologram.format", ConfigOptionType.STRING, "visual", Material.OAK_SIGN,
                "Hologramm-Format", "MiniMessage Format");
        register("bossbar-enabled", "settings.drone.bossbar.enabled", ConfigOptionType.BOOLEAN, "visual", Material.EXPERIENCE_BOTTLE,
                "Bossbar", "Bossbar an/aus");
        register("bossbar-format", "settings.drone.bossbar.format", ConfigOptionType.STRING, "visual", Material.OAK_SIGN,
                "Bossbar-Format", "Format für Spieler-Ziele");
        register("bossbar-format-socket", "settings.drone.bossbar.format-socket", ConfigOptionType.STRING, "visual", Material.OAK_SIGN,
                "Bossbar Socket-Format", "Format für Socket-Ziele");
        registerEnum("bossbar-color", "settings.drone.bossbar.color", "visual", Material.RED_DYE,
                "Bossbar-Farbe", "BarColor Wert", "PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE");
        register("locate-particles-enabled", "settings.drone.locate-particles.enabled", ConfigOptionType.BOOLEAN, "visual", Material.GLOWSTONE,
                "Locate-Partikel", "Partikel beim Auffinden");
        register("locate-particles-particle", "settings.drone.locate-particles.particle", ConfigOptionType.STRING, "visual", Material.GLOWSTONE_DUST,
                "Locate-Partikel-Typ", "Partikel-Name");

        register("collection-animation-enabled", "settings.drone.collection-animation.enabled", ConfigOptionType.BOOLEAN, "animations", Material.ITEM_FRAME,
                "Sammel-Animation", "Animation beim Einsammeln");
        register("launch-animation-enabled", "settings.drone.launch-animation.enabled", ConfigOptionType.BOOLEAN, "animations", Material.FIREWORK_ROCKET,
                "Start-Animation", "Animation beim Start");
        register("launch-animation-seconds", "settings.drone.launch-animation.seconds", ConfigOptionType.INT, "animations", Material.CLOCK,
                "Start-Dauer", "Sekunden der Startanimation");
        register("launch-animation-sound", "settings.drone.launch-animation.sound", ConfigOptionType.STRING, "animations", Material.NOTE_BLOCK,
                "Start-Sound", "Sound beim Start");
        register("launch-animation-sound-volume", "settings.drone.launch-animation.sound-volume", ConfigOptionType.DOUBLE, "animations", Material.JUKEBOX,
                "Start-Lautstärke", "Lautstärke 0.0–1.0");

        register("container-integration-enabled", "settings.drone.container-integration.enabled", ConfigOptionType.BOOLEAN, "containers", Material.HOPPER,
                "Container-Integration", "Automatisches Entladen");
        register("container-search-radius", "settings.drone.container-integration.search-radius", ConfigOptionType.INT, "containers", Material.COMPASS,
                "Suchradius", "Radius um Socket");

        register("discord-enabled", "discord.enabled", ConfigOptionType.BOOLEAN, "discord", Material.LEVER,
                "Discord aktiv", "Webhook-Benachrichtigungen");
        register("discord-webhook-url", "discord.webhook-url", ConfigOptionType.STRING, "discord", Material.MAP,
                "Webhook-URL", "Discord Webhook URL");
        register("discord-username", "discord.username", ConfigOptionType.STRING, "discord", Material.NAME_TAG,
                "Bot-Name", "Anzeigename im Discord");
        register("discord-avatar-url", "discord.avatar-url", ConfigOptionType.STRING, "discord", Material.PLAYER_HEAD,
                "Avatar-URL", "Profilbild URL");
        register("discord-embed-enabled", "discord.embed.enabled", ConfigOptionType.BOOLEAN, "discord", Material.PAINTING,
                "Embed aktiv", "Rich Embed senden");
        register("discord-embed-color", "discord.embed.color", ConfigOptionType.STRING, "discord", Material.RED_DYE,
                "Embed-Farbe", "Hex-Farbe z.B. #00ff00");
        register("discord-embed-thumbnail-enabled", "discord.embed.thumbnail.enabled", ConfigOptionType.BOOLEAN, "discord", Material.ITEM_FRAME,
                "Thumbnail aktiv", "Vorschaubild im Embed");
        register("discord-embed-thumbnail-url", "discord.embed.thumbnail.url", ConfigOptionType.STRING, "discord", Material.MAP,
                "Thumbnail-URL", "URL des Vorschaubilds");
        register("discord-embed-image-enabled", "discord.embed.image.enabled", ConfigOptionType.BOOLEAN, "discord", Material.PAINTING,
                "Bild aktiv", "Großes Bild im Embed");
        register("discord-embed-image-url", "discord.embed.image.url", ConfigOptionType.STRING, "discord", Material.MAP,
                "Bild-URL", "URL des Embed-Bildes");
        register("discord-embed-footer-text", "discord.embed.footer.text", ConfigOptionType.STRING, "discord", Material.OAK_SIGN,
                "Footer-Text", "Text in der Fußzeile");
        register("discord-embed-footer-icon", "discord.embed.footer.icon-url", ConfigOptionType.STRING, "discord", Material.ITEM_FRAME,
                "Footer-Icon", "Icon URL der Fußzeile");
        register("discord-include-items", "discord.include-items", ConfigOptionType.BOOLEAN, "discord", Material.CHEST,
                "Items anzeigen", "Items im Embed listen");
        register("discord-include-animals", "discord.include-animals", ConfigOptionType.BOOLEAN, "discord", Material.LEAD,
                "Tiere anzeigen", "Tiere im Embed listen");
        register("discord-max-items", "discord.max-items-display", ConfigOptionType.INT, "discord", Material.CHEST,
                "Max. Items", "Max. Items im Embed");
        register("discord-max-animals", "discord.max-animals-display", ConfigOptionType.INT, "discord", Material.LEAD,
                "Max. Tiere", "Max. Tiere im Embed");
    }

    private ConfigEditorRegistry() {
    }

    private static void register(String id, String path, ConfigOptionType type, String category, Material icon, String name, String description) {
        ConfigOption option = new ConfigOption(id, path, type, category, icon, name, description);
        OPTIONS_BY_ID.put(id, option);
        OPTIONS_BY_CATEGORY.computeIfAbsent(category, key -> new ArrayList<>()).add(option);
    }

    private static void registerEnum(String id, String path, String category, Material icon, String name, String description, String... values) {
        ConfigOption option = new ConfigOption(id, path, ConfigOptionType.ENUM, category, icon, name, description, List.of(values));
        OPTIONS_BY_ID.put(id, option);
        OPTIONS_BY_CATEGORY.computeIfAbsent(category, key -> new ArrayList<>()).add(option);
    }

    public static List<ConfigCategory> categories() {
        return CATEGORIES;
    }

    public static Optional<ConfigCategory> category(String id) {
        return CATEGORIES.stream().filter(category -> category.id().equals(id)).findFirst();
    }

    public static List<ConfigOption> optionsForCategory(String categoryId) {
        return OPTIONS_BY_CATEGORY.getOrDefault(categoryId, List.of());
    }

    public static Optional<ConfigOption> option(String id) {
        return Optional.ofNullable(OPTIONS_BY_ID.get(id));
    }

    public static Map<String, ConfigOption> allOptions() {
        return Collections.unmodifiableMap(OPTIONS_BY_ID);
    }
}
