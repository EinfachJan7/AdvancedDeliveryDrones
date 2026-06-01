package de.cb.drones.util;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * Resolves {@code drone.send.max.<number>} permissions (LuckPerms-compatible via Bukkit effective permissions).
 */
public final class SendMaxPermissions {

    private static final String PREFIX = "drone.send.max.";

    private SendMaxPermissions() {
    }

    /**
     * Returns the highest granted send limit from permissions, or {@code 0} if none is set.
     */
    public static int resolveMaxFromPermissions(Player player) {
        if (player == null) {
            return 0;
        }
        int max = 0;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission().toLowerCase();
            if (!permission.startsWith(PREFIX)) {
                continue;
            }
            String suffix = permission.substring(PREFIX.length());
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
