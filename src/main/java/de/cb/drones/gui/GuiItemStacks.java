package de.cb.drones.gui;

import de.cb.drones.drone.GuiItem;
import de.cb.drones.util.SkullTextureUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class GuiItemStacks {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private GuiItemStacks() {
    }

    public static ItemStack create(GuiItem item) {
        return create(item, null);
    }

    public static ItemStack create(GuiItem item, Consumer<ItemMeta> metaCustomizer) {
        ItemStack stack = de.cb.drones.drone.CustomItemHook.getCustomItem(item.customModelProvider(), item.customModelId());
        if (stack == null) {
            stack = new ItemStack(item.material());
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(MINI_MESSAGE.deserialize(item.name()));
        if (item.lore() != null && !item.lore().isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : item.lore()) {
                loreComponents.add(MINI_MESSAGE.deserialize(line));
            }
            meta.lore(loreComponents);
        }
        applyHeadTexture(meta, item.material(), item.headTexture());
        if (item.enchanted()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (metaCustomizer != null) {
            metaCustomizer.accept(meta);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static void applyHeadTexture(ItemMeta meta, Material material, String headTexture) {
        if (headTexture == null || headTexture.isBlank() || material != Material.PLAYER_HEAD || !(meta instanceof SkullMeta skullMeta)) {
            return;
        }
        SkullTextureUtils.applyTexture(skullMeta, headTexture);
    }

    public static int normalizeInventorySize(int requested) {
        int clamped = Math.min(54, Math.max(9, requested));
        return clamped - (clamped % 9);
    }
}
