package de.cb.drones.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;

public final class SkullTextureUtils {

    private SkullTextureUtils() {
    }

    public static void applyTexture(SkullMeta meta, String base64Texture) {
        if (meta == null || base64Texture == null || base64Texture.isBlank()) {
            return;
        }
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "head");
            profile.setProperty(new ProfileProperty("textures", base64Texture.trim()));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // keep default skull when profile api fails
        }
    }

    public static void applyOwningPlayer(SkullMeta meta, OfflinePlayer player) {
        if (meta == null || player == null) {
            return;
        }
        if (player instanceof Player online) {
            meta.setPlayerProfile(online.getPlayerProfile());
            return;
        }
        meta.setOwningPlayer(player);
    }
}
