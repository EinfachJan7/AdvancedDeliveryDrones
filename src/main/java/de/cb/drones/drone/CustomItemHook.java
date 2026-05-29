package de.cb.drones.drone;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CustomItemHook {

    public static ItemStack getCustomItem(DroneSettings settings) {
        String provider = settings.customModelProvider();
        if (provider == null || provider.isBlank() || "NONE".equalsIgnoreCase(provider)) {
            return null; // Fallback to skull texture
        }

        try {
            switch (provider.toUpperCase()) {
                case "NATIVE":
                    return getNativeCustomItem(settings);
                case "NEXO":
                    return getNexoItem(settings.customModelItemId());
                case "ORAXEN":
                    return getOraxenItem(settings.customModelItemId());
                case "ITEMSADDER":
                    return getItemsAdderItem(settings.customModelItemId());
                default:
                    Bukkit.getLogger().warning("[AdvancedDeliveryDrones] Unknown custom model provider: " + provider);
                    return null;
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AdvancedDeliveryDrones] Failed to load custom item from provider " + provider + " for id " + settings.customModelItemId() + ": " + e.getMessage());
            return null;
        }
    }

    private static ItemStack getNativeCustomItem(DroneSettings settings) {
        if (settings.nativeData() > 0) {
            ItemStack customModel = new ItemStack(settings.nativeMaterial());
            ItemMeta meta = customModel.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(settings.nativeData());
                meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gold>Drone</gold>"));
                customModel.setItemMeta(meta);
            }
            return customModel;
        }
        return null;
    }

    private static ItemStack getNexoItem(String id) throws Exception {
        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
            Bukkit.getLogger().warning("[AdvancedDeliveryDrones] Nexo is not enabled!");
            return null;
        }
        Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
        Object itemBuilder = nexoItemsClass.getMethod("itemFromId", String.class).invoke(null, id);
        if (itemBuilder != null) {
            return (ItemStack) itemBuilder.getClass().getMethod("build").invoke(itemBuilder);
        }
        return null;
    }

    private static ItemStack getOraxenItem(String id) throws Exception {
        if (!Bukkit.getPluginManager().isPluginEnabled("Oraxen")) {
            Bukkit.getLogger().warning("[AdvancedDeliveryDrones] Oraxen is not enabled!");
            return null;
        }
        Class<?> oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
        Object itemBuilder = oraxenItemsClass.getMethod("getItemById", String.class).invoke(null, id);
        if (itemBuilder != null) {
            return (ItemStack) itemBuilder.getClass().getMethod("build").invoke(itemBuilder);
        }
        return null;
    }

    private static ItemStack getItemsAdderItem(String id) throws Exception {
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            Bukkit.getLogger().warning("[AdvancedDeliveryDrones] ItemsAdder is not enabled!");
            return null;
        }
        Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
        Object stackInst = customStackClass.getMethod("getInstance", String.class).invoke(null, id);
        if (stackInst != null) {
            return (ItemStack) customStackClass.getMethod("getItemStack").invoke(stackInst);
        }
        return null;
    }
}
