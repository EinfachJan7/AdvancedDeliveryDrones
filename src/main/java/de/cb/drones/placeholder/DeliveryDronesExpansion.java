package de.cb.drones.placeholder;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.config.PlayerBlacklistRepository;
import de.cb.drones.config.PlayerSettingsRepository;
import de.cb.drones.drone.DeliveryDrone;
import de.cb.drones.drone.DroneManager;
import de.cb.drones.drone.DroneSettings;
import de.cb.drones.socket.DeliverySocket;
import de.cb.drones.socket.SocketRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion (optional). Identifier: {@code deliverydrones}
 * <p>
 * Examples: {@code %deliverydrones_outgoing_count%}, {@code %deliverydrones_incoming_1_eta%},
 * {@code %deliverydrones_socket_2_world%}, {@code %deliverydrones_config_speed%},
 * {@code %deliverydrones_id_<uuid>_receiver%}
 */
public final class DeliveryDronesExpansion extends PlaceholderExpansion {

    private static final Pattern INDEXED = Pattern.compile("^(outgoing|incoming|socket)_(?:(\\d+)_)?(.+)$");
    private static final Pattern DRONE_BY_ID = Pattern.compile("^(?:id|drone)_([0-9a-fA-F-]{32,36})_(.+)$");
    private static final Pattern PLAYER_NAME = Pattern.compile("^(?:playername|player_name|name|uuid_to_name)_([0-9a-fA-F-]{32,36})$");
    private static final Pattern DRONE_ITEM_INDEX = Pattern.compile("^item_(\\d+)_(name|amount|material|type)$");
    private static final Pattern DRONE_ANIMAL_INDEX = Pattern.compile("^animal_(\\d+)_(name|type)$");

    private final AdvancedDeliveryDronesPlugin plugin;

    public DeliveryDronesExpansion(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "deliverydrones";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        DroneManager drones = plugin.getDroneManager();
        if (drones == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT).trim();
        if (key.isEmpty()) {
            return "";
        }

        Matcher playerNameMatcher = PLAYER_NAME.matcher(key);
        if (playerNameMatcher.matches()) {
            return PlaceholderPlayerNames.fromUuidString(playerNameMatcher.group(1));
        }

        String flat = resolvePlayerOrGlobal(plugin, drones, player, key);
        if (flat != null) {
            return flat;
        }

        Matcher droneIdMatcher = DRONE_BY_ID.matcher(key);
        if (droneIdMatcher.matches()) {
            return emptyIfNull(resolveDroneById(drones, droneIdMatcher.group(1), droneIdMatcher.group(2)));
        }

        if (key.startsWith("config_")) {
            return emptyIfNull(resolveConfig(drones.settings(), key.substring("config_".length())));
        }

        Matcher indexedMatcher = INDEXED.matcher(key);
        if (indexedMatcher.matches()) {
            String category = indexedMatcher.group(1);
            int index = indexedMatcher.group(2) == null ? 1 : Integer.parseInt(indexedMatcher.group(2));
            String field = indexedMatcher.group(3);
            String indexed = switch (category) {
                case "outgoing" -> resolveOutgoingDrone(drones, player, index, field);
                case "incoming" -> resolveIncomingDrone(drones, player, index, field);
                case "socket" -> resolveSocket(plugin.getSocketRepository(), drones, player, index, field);
                default -> null;
            };
            return emptyIfNull(indexed);
        }

        return "";
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private String resolvePlayerOrGlobal(
            AdvancedDeliveryDronesPlugin plugin,
            DroneManager drones,
            Player player,
            String key
    ) {
        UUID id = player.getUniqueId();
        PlayerSettingsRepository settings = plugin.getPlayerSettings();
        PlayerBlacklistRepository blacklist = plugin.getBlacklistRepository();
        SocketRepository sockets = plugin.getSocketRepository();
        DroneSettings droneSettings = drones.settings();
        var cfg = plugin.getConfig();
        String section = "settings.drone.";

        return switch (key) {
            case "version", "plugin_version" -> plugin.getDescription().getVersion();
            case "can_receive", "receive_enabled" -> bool(settings.canReceive(id));
            case "outgoing_count" -> String.valueOf(drones.countOutgoing(id));
            case "incoming_count" -> String.valueOf(drones.countIncoming(id));
            case "incoming_flying_count" -> String.valueOf(drones.countIncomingFlying(id));
            case "incoming_landed_count" -> String.valueOf(drones.countIncomingLanded(id));
            case "outgoing_flying_count" -> String.valueOf(drones.getOutgoingDrones(id).stream().filter(DeliveryDrone::isFlying).count());
            case "outgoing_landed_count" -> String.valueOf(drones.getOutgoingDrones(id).stream().filter(DeliveryDrone::isLanded).count());
            case "max_inventory_size" -> String.valueOf(droneSettings.inventorySize());
            case "active_outgoing", "outgoing_active" -> String.valueOf(drones.activeOutgoingCount(id));
            case "active_slots_max", "max_active" -> String.valueOf(drones.maxActiveForSender(id));
            case "can_send", "can_launch" -> bool(drones.canSenderLaunch(id));
            case "blacklist_count" -> String.valueOf(blacklist.getPlayerBlacklist(id).size());
            case "blacklist_names" -> PlaceholderPlayerNames.joinNames(blacklist.getPlayerBlacklist(id));
            case "socket_count" -> String.valueOf(sockets.getSocketsByOwner(id).size());
            case "socket_max", "max_sockets" -> String.valueOf(drones.maxSocketsFor(player));
            case "socket_names" -> joinSocketNames(sockets.getSocketsByOwner(id));
            case "socket_slots_free" -> String.valueOf(Math.max(0,
                    drones.maxSocketsFor(player) - sockets.getSocketsByOwner(id).size()));
            case "max_leashed_animals_player", "leashed_max" ->
                    String.valueOf(drones.maxLeashedAnimalsFor(player));
            case "cooldown_player", "send_cooldown_player" ->
                    String.valueOf(cfg.getInt(section + "send-cooldown-seconds-player", 0));
            case "cooldown_socket", "send_cooldown_socket" ->
                    String.valueOf(cfg.getInt(section + "send-cooldown-seconds-socket", 0));
            case "cooldown_player_remaining", "send_cooldown_player_remaining" -> String.valueOf(
                    settings.getRemainingCooldown(id, cfg.getInt(section + "send-cooldown-seconds-player", 0)));
            case "cooldown_socket_remaining", "send_cooldown_socket_remaining" -> String.valueOf(
                    settings.getRemainingCooldown(id, cfg.getInt(section + "send-cooldown-seconds-socket", 0)));
            case "pending_returns" -> String.valueOf(drones.pendingReturnStacks(id));
            case "has_incoming" -> bool(drones.countIncoming(id) > 0);
            case "has_outgoing" -> bool(drones.countOutgoing(id) > 0);
            case "has_landed_incoming" -> bool(drones.countIncomingLanded(id) > 0);
            case "nearest_landed_distance" -> formatNearestLanded(drones, player, "distance");
            case "nearest_landed_world" -> formatNearestLanded(drones, player, "world");
            case "nearest_landed_x" -> formatNearestLanded(drones, player, "x");
            case "nearest_landed_y" -> formatNearestLanded(drones, player, "y");
            case "nearest_landed_z" -> formatNearestLanded(drones, player, "z");
            case "nearest_landed_uuid" -> formatNearestLanded(drones, player, "uuid");
            case "nearest_landed_sender" -> formatNearestLanded(drones, player, "sender");
            case "nearest_landed_sender_name" -> formatNearestLanded(drones, player, "sender");
            case "nearest_incoming_distance" -> formatNearestIncoming(drones, player, "distance");
            case "nearest_incoming_world" -> formatNearestIncoming(drones, player, "world");
            case "nearest_incoming_x" -> formatNearestIncoming(drones, player, "x");
            case "nearest_incoming_y" -> formatNearestIncoming(drones, player, "y");
            case "nearest_incoming_z" -> formatNearestIncoming(drones, player, "z");
            case "nearest_incoming_uuid" -> formatNearestIncoming(drones, player, "uuid");
            case "nearest_incoming_sender" -> formatNearestIncoming(drones, player, "sender");
            case "nearest_incoming_sender_name" -> formatNearestIncoming(drones, player, "sender");
            case "players_enabled" -> bool(droneSettings.playersEnabled());
            case "sockets_enabled" -> bool(droneSettings.socketsEnabled());
            case "glowing_enabled" -> bool(droneSettings.glowingEnabled());
            case "custom_model_provider" -> droneSettings.customModelProvider();
            case "custom_model_item_id" -> droneSettings.customModelItemId();
            case "total_drones", "active_drones" -> String.valueOf(drones.activeDronesSnapshot().size());
            case "total_flying" -> String.valueOf(drones.activeDronesSnapshot().stream().filter(DeliveryDrone::isFlying).count());
            case "total_landed" -> String.valueOf(drones.activeDronesSnapshot().stream().filter(DeliveryDrone::isLanded).count());
            case "database_type" -> cfg.getString("database.type", "YAML");
            case "language" -> cfg.getString("language", "de_DE");
            case "total_sockets" -> String.valueOf(plugin.getSocketRepository().getAllSockets().size());
            case "discord_webhook_enabled" -> bool(plugin.getDiscordWebhookManager().isEnabled());
            case "wg_hook_enabled" -> bool(de.cb.drones.util.WorldGuardHook.isEnabled());
            default -> null;
        };
    }

    private String resolveConfig(DroneSettings settings, String field) {
        return switch (field) {
            case "speed" -> formatDouble(settings.speed());
            case "startup_speed" -> formatDouble(settings.startupSpeed());
            case "startup_seconds" -> String.valueOf(settings.startupSeconds());
            case "approach_speed" -> formatDouble(settings.approachSpeed());
            case "approach_distance" -> formatDouble(settings.approachDistance());
            case "delivery_radius" -> formatDouble(settings.deliveryRadius());
            case "despawn_minutes" -> String.valueOf(settings.despawnTicks() / 20L / 60L);
            case "despawn_mode" -> settings.despawnMode().name();
            case "inventory_size" -> String.valueOf(settings.inventorySize());
            case "max_active_per_sender" -> String.valueOf(settings.maxActivePerSender());
            case "max_sockets_per_player" -> String.valueOf(settings.maxSocketsPerPlayer());
            case "max_leashed_animals" -> String.valueOf(settings.maxLeashedAnimalsPerDrone());
            case "carry_leashed_animals" -> bool(settings.carryLeashedAnimals());
            case "follow_gliding" -> bool(settings.followGlidingPlayer());
            case "follow_airborne" -> bool(settings.followAirbornePlayerBeforeLanding());
            case "airborne_follow_min_height" -> formatDouble(settings.airborneFollowMinHeight());
            case "airborne_follow_max_seconds" -> String.valueOf(settings.airborneFollowMaxSecondsAfterStart());
            case "hologram_enabled" -> bool(settings.hologramEnabled());
            case "bossbar_enabled" -> bool(settings.bossbarEnabled());
            case "container_integration" -> bool(settings.containerIntegrationEnabled());
            case "container_search_radius" -> String.valueOf(settings.containerIntegrationSearchRadius());
            case "launch_animation" -> bool(settings.launchAnimationEnabled());
            case "collection_animation" -> bool(settings.collectionAnimationEnabled());
            case "locate_particles" -> bool(settings.locateParticlesEnabled());
            case "animal_return_mode" -> settings.animalReturnMode().name();
            case "particle_count" -> String.valueOf(settings.particleCount());
            case "particle_trail_length" -> String.valueOf(settings.particleTrailLength());
            case "glowing_enabled" -> bool(settings.glowingEnabled());
            case "socket_name_use_allowed_list" -> bool(settings.socketNameUseAllowedList());
            case "mob_sending_enabled", "animal_selection_enabled" -> bool(settings.animalSelectionEnabled());
            case "mob_sending_radius", "animal_selection_radius" -> formatDouble(settings.animalSelectionRadius());
            case "mob_sending_leashable_only", "animal_selection_leashable_only" -> bool(settings.animalSelectionLeashableOnly());
            case "blocked_worlds_count" -> String.valueOf(settings.blockedWorlds().size());
            case "mob_sending_blacklist_count" -> String.valueOf(settings.mobSendingBlacklist().size());
            case "compose_item_blacklist_count" -> String.valueOf(settings.composeItemBlacklist().size());
            default -> null;
        };
    }

    private String resolveOutgoingDrone(DroneManager drones, Player player, int index, String field) {
        List<DeliveryDrone> list = drones.getOutgoingDrones(player.getUniqueId());
        if (index < 1 || index > list.size()) {
            return "";
        }
        return resolveDroneField(list.get(index - 1), field, player);
    }

    private String resolveIncomingDrone(DroneManager drones, Player player, int index, String field) {
        List<DeliveryDrone> list = drones.getIncomingDrones(player.getUniqueId());
        if (index < 1 || index > list.size()) {
            return "";
        }
        return resolveDroneField(list.get(index - 1), field, player);
    }

    private String resolveDroneById(DroneManager drones, String rawId, String field) {
        UUID droneId;
        try {
            droneId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            return "";
        }
        DeliveryDrone drone = drones.findByDroneId(droneId);
        if (drone == null) {
            return "";
        }
        Player context = Bukkit.getPlayer(drone.receiverId());
        if (context == null) {
            context = Bukkit.getPlayer(drone.senderId());
        }
        return resolveDroneField(drone, field, context);
    }

    private String resolveSocket(SocketRepository sockets, DroneManager drones, Player player, int index, String field) {
        List<DeliverySocket> list = sockets.getSocketsByOwner(player.getUniqueId());
        if (index < 1 || index > list.size()) {
            return "";
        }
        DeliverySocket socket = list.get(index - 1);
        Location loc = socket.location();
        return switch (field) {
            case "name" -> socket.name();
            case "world" -> socket.getWorldName();
            case "x" -> String.valueOf(loc.getBlockX());
            case "y" -> String.valueOf(loc.getBlockY());
            case "z" -> String.valueOf(loc.getBlockZ());
            case "coords", "coordinates" -> socket.getCoordinates();
            case "owner" -> socket.ownerName();
            case "trusted_count" -> String.valueOf(socket.trustedPlayers().size());
            case "trusted_names" -> PlaceholderPlayerNames.joinNames(socket.trustedPlayers());
            case "blacklist_count" -> String.valueOf(socket.blacklistedPlayers().size());
            case "blacklist_names" -> PlaceholderPlayerNames.joinNames(socket.blacklistedPlayers());
            case "created" -> String.valueOf(socket.createdTimestamp());
            case "uuid", "id" -> socket.socketId().toString();
            case "has_incoming" -> bool(drones.isDroneFlyingToSocket(socket.name()));
            case "is_owner" -> bool(player.getUniqueId().equals(socket.ownerId()));
            default -> null;
        };
    }

    private String resolveDroneField(DeliveryDrone drone, String field, Player viewer) {
        Matcher itemMatcher = DRONE_ITEM_INDEX.matcher(field);
        if (itemMatcher.matches()) {
            return emptyIfNull(resolveDroneItemField(drone, Integer.parseInt(itemMatcher.group(1)), itemMatcher.group(2)));
        }

        Matcher animalMatcher = DRONE_ANIMAL_INDEX.matcher(field);
        if (animalMatcher.matches()) {
            return emptyIfNull(resolveDroneAnimalField(drone, Integer.parseInt(animalMatcher.group(1)), animalMatcher.group(2)));
        }

        Location loc = drone.currentLocation();
        Location target = drone.targetLocation();
        return switch (field) {
            case "uuid", "id" -> drone.droneId().toString();
            case "stand_uuid", "entity_uuid" -> drone.standId() == null ? "" : drone.standId().toString();
            case "sender", "sender_name" -> PlaceholderPlayerNames.fromUuid(drone.senderId());
            case "sender_uuid" -> drone.senderId().toString();
            case "receiver", "receiver_name" -> {
                if (drone.isSocketDelivery()) {
                    yield drone.receiverName() == null || drone.receiverName().isBlank()
                            ? PlaceholderPlayerNames.fromUuid(drone.receiverId())
                            : drone.receiverName();
                }
                yield PlaceholderPlayerNames.fromUuid(drone.receiverId());
            }
            case "receiver_uuid" -> drone.receiverId().toString();
            case "socket", "socket_name" -> drone.socketName() == null ? "" : drone.socketName();
            case "is_socket" -> bool(drone.isSocketDelivery());
            case "world" -> loc.getWorld() == null ? "" : loc.getWorld().getName();
            case "x" -> String.valueOf(loc.getBlockX());
            case "y" -> String.valueOf(loc.getBlockY());
            case "z" -> String.valueOf(loc.getBlockZ());
            case "target_world" -> target.getWorld() == null ? "" : target.getWorld().getName();
            case "target_x" -> String.valueOf(target.getBlockX());
            case "target_y" -> String.valueOf(target.getBlockY());
            case "target_z" -> String.valueOf(target.getBlockZ());
            case "distance", "distance_target" -> String.valueOf(drone.distanceToTargetMeters());
            case "eta", "eta_seconds" -> String.valueOf(drone.estimatedEtaSeconds());
            case "flying", "is_flying" -> bool(drone.isFlying());
            case "landed", "is_landed" -> bool(drone.isLanded());
            case "is_returning" -> bool(drone.isReturningToSender());
            case "is_animating" -> bool(drone.isAnimating());
            case "opened", "was_opened" -> bool(drone.wasOpenedByReceiver());
            case "animals_only" -> bool(drone.animalsOnlyDelivery());
            case "item_count", "items" -> String.valueOf(drone.filledInventorySlots());
            case "items_total", "items_total_amount", "item_amount_total" -> String.valueOf(drone.totalItemAmount());
            case "items_summary", "contents_items" -> drone.formatItemsSummary();
            case "items_list", "items_slots", "items_slot_list" -> drone.formatItemsList();
            case "animal_count", "animals" -> String.valueOf(drone.attachedAnimalCount());
            case "animals_summary", "animals_list", "contents_animals" -> drone.formatAnimalsSummary();
            case "animals_types", "animals_type_list" -> drone.formatAnimalsList();
            case "has_items" -> bool(drone.filledInventorySlots() > 0);
            case "has_animals" -> bool(drone.attachedAnimalCount() > 0);
            case "contents_summary" -> {
                String items = drone.formatItemsSummary();
                String animals = drone.formatAnimalsSummary();
                if (items.isEmpty()) {
                    yield animals;
                }
                if (animals.isEmpty()) {
                    yield items;
                }
                yield items + ", " + animals;
            }
            case "despawn_seconds", "despawn_remaining" -> {
                int remaining = drone.despawnSecondsRemaining();
                yield remaining < 0 ? "" : String.valueOf(remaining);
            }
            case "distance_player" -> {
                if (viewer == null || loc.getWorld() == null || !loc.getWorld().equals(viewer.getWorld())) {
                    yield "";
                }
                yield String.valueOf((int) Math.round(viewer.getLocation().distance(loc)));
            }
            default -> null;
        };
    }

    private String resolveDroneItemField(DeliveryDrone drone, int index, String property) {
        org.bukkit.inventory.ItemStack stack = drone.inventoryItemAt(index);
        if (stack == null) {
            return "";
        }
        return switch (property) {
            case "name" -> formatItemDisplayName(stack);
            case "amount" -> String.valueOf(stack.getAmount());
            case "material", "type" -> stack.getType().name();
            default -> null;
        };
    }

    private String resolveDroneAnimalField(DeliveryDrone drone, int index, String property) {
        org.bukkit.entity.EntityType type = drone.animalTypeAt(index);
        if (type == null) {
            return "";
        }
        String formatted = type.getKey().getKey().replace('_', ' ');
        if (!formatted.isEmpty()) {
            formatted = formatted.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + formatted.substring(1);
        }
        return switch (property) {
            case "name" -> formatted;
            case "type" -> type.name();
            default -> null;
        };
    }

    private static String formatItemDisplayName(org.bukkit.inventory.ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(stack.getItemMeta().displayName());
        }
        String key = stack.getType().getKey().getKey().replace('_', ' ');
        if (key.isEmpty()) {
            return stack.getType().name();
        }
        return key.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + key.substring(1);
    }

    private String formatNearestLanded(DroneManager drones, Player player, String field) {
        DeliveryDrone drone = drones.findNearestLandedDrone(player);
        if (drone == null) {
            return "";
        }
        Location loc = drone.currentLocation();
        return switch (field) {
            case "distance" -> {
                if (loc.getWorld() == null || !loc.getWorld().equals(player.getWorld())) {
                    yield "";
                }
                yield String.valueOf((int) Math.round(player.getLocation().distance(loc)));
            }
            case "world" -> loc.getWorld() == null ? "" : loc.getWorld().getName();
            case "x" -> String.valueOf(loc.getBlockX());
            case "y" -> String.valueOf(loc.getBlockY());
            case "z" -> String.valueOf(loc.getBlockZ());
            case "uuid" -> drone.droneId().toString();
            case "sender" -> PlaceholderPlayerNames.fromUuid(drone.senderId());
            case "sender_name" -> PlaceholderPlayerNames.fromUuid(drone.senderId());
            default -> "";
        };
    }

    private String formatNearestIncoming(DroneManager drones, Player player, String field) {
        DeliveryDrone nearest = null;
        double nearestSq = Double.MAX_VALUE;
        Location playerLoc = player.getLocation();
        for (DeliveryDrone drone : drones.getIncomingDrones(player.getUniqueId())) {
            Location droneLoc = drone.currentLocation();
            if (droneLoc.getWorld() == null || !droneLoc.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distSq = droneLoc.distanceSquared(playerLoc);
            if (distSq < nearestSq) {
                nearestSq = distSq;
                nearest = drone;
            }
        }
        if (nearest == null) {
            return "";
        }
        Location loc = nearest.currentLocation();
        return switch (field) {
            case "distance" -> String.valueOf((int) Math.round(player.getLocation().distance(loc)));
            case "world" -> loc.getWorld() == null ? "" : loc.getWorld().getName();
            case "x" -> String.valueOf(loc.getBlockX());
            case "y" -> String.valueOf(loc.getBlockY());
            case "z" -> String.valueOf(loc.getBlockZ());
            case "uuid" -> nearest.droneId().toString();
            case "sender" -> PlaceholderPlayerNames.fromUuid(nearest.senderId());
            case "sender_name" -> PlaceholderPlayerNames.fromUuid(nearest.senderId());
            default -> "";
        };
    }

    private static String joinSocketNames(List<DeliverySocket> sockets) {
        if (sockets.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sockets.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(sockets.get(i).name());
        }
        return builder.toString();
    }

    private static String bool(boolean value) {
        return value ? "true" : "false";
    }

    private static String formatDouble(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
