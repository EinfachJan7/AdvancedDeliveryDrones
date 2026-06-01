package de.cb.drones.util;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * Resolves numeric drone limit permissions (LuckPerms-compatible via Bukkit effective permissions).
 * <ul>
 *   <li>{@code drone.send.max.&lt;n&gt;} – concurrent outgoing drones</li>
 *   <li>{@code drone.leashed.max.&lt;n&gt;} – leashed animals per drone</li>
 *   <li>{@code drone.sockets.max.&lt;n&gt;} – delivery sockets per player</li>
 * </ul>
 */
public final class SendMaxPermissions {

    public static final String SEND_MAX_PREFIX = "drone.send.max.";
    public static final String LEASHED_MAX_PREFIX = "drone.leashed.max.";
    public static final String SOCKETS_MAX_PREFIX = "drone.sockets.max.";
    /** @deprecated use {@link #LEASHED_MAX_PREFIX} */
    public static final String LEASH_MAX_PREFIX = "drone.leash.max.";
    /** @deprecated use {@link #SOCKETS_MAX_PREFIX} */
    public static final String SOCKET_MAX_PREFIX = "drone.socket.max.";

    private SendMaxPermissions() {
    }

    /**
     * Returns the highest granted send limit from permissions, or {@code 0} if none is set.
     */
    public static int resolveMaxFromPermissions(Player player) {
        return resolveMaxFromPermissions(player, SEND_MAX_PREFIX);
    }

    public static int resolveLeashedMaxFromPermissions(Player player) {
        int max = resolveMaxFromPermissions(player, LEASHED_MAX_PREFIX);
        return max > 0 ? max : resolveMaxFromPermissions(player, LEASH_MAX_PREFIX);
    }

    public static int resolveSocketsMaxFromPermissions(Player player) {
        int max = resolveMaxFromPermissions(player, SOCKETS_MAX_PREFIX);
        return max > 0 ? max : resolveMaxFromPermissions(player, SOCKET_MAX_PREFIX);
    }

    /**
     * Returns the highest granted limit for the given permission prefix, or {@code 0} if none is set.
     */
    public static int resolveMaxFromPermissions(Player player, String prefix) {
        if (player == null || prefix == null || prefix.isEmpty()) {
            return 0;
        }
        String normalizedPrefix = prefix.toLowerCase();
        int max = 0;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission().toLowerCase();
            if (!permission.startsWith(normalizedPrefix)) {
                continue;
            }
            String suffix = permission.substring(normalizedPrefix.length());
            if (suffix.isEmpty()) {
                continue;
            }
            try {
                int value = Integer.parseInt(suffix);
                if (value > 0) {
                    max = Math.max(max, value);
                }
            } catch (NumberFormatException ignored) {
                // ignore malformed permission nodes
            }
        }
        return max;
    }
}
