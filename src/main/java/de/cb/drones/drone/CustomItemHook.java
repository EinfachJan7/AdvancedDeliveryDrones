package de.cb.drones.drone;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

public class CustomItemHook {

    public static ItemStack getCustomItem(DroneSettings settings) {
        return getCustomItem(settings.customModelProvider(), settings.customModelItemId());
    }

    public static ItemStack getCustomItem(String provider, String itemId) {
        if (provider == null || provider.isBlank() || "NONE".equalsIgnoreCase(provider)) {
            return null; // Fallback to normal behavior
        }

        try {
            switch (provider.toUpperCase()) {
                case "NEXO":
                    return getNexoItem(itemId);
                case "ORAXEN":
                    return getOraxenItem(itemId);
                case "ITEMSADDER":
                    return getItemsAdderItem(itemId);
                default:
                    Bukkit.getLogger().warning("[AdvancedDeliveryDrones] Unknown custom model provider: " + provider);
                    return null;
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[AdvancedDeliveryDrones] Failed to load custom item from provider " + provider + " for id " + itemId + ": " + e.getMessage());
            return null;
        }
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
