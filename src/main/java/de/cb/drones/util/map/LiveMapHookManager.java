package de.cb.drones.util.map;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

public final class LiveMapHookManager {
    private final AdvancedDeliveryDronesPlugin plugin;
    private final List<LiveMapHook> hooks = new ArrayList<>();

    public LiveMapHookManager(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        tryLoad("Pl3xMap", "de.cb.drones.util.map.pl3x.Pl3xmapLiveMapHook");
        tryLoad("BlueMap", "de.cb.drones.util.map.bluemap.BluemapLiveMapHook");
    }

    private void tryLoad(String pluginName, String className) {
        if (Bukkit.getPluginManager().getPlugin(pluginName) == null) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(className);
            LiveMapHook hook = (LiveMapHook) clazz.getConstructor(AdvancedDeliveryDronesPlugin.class).newInstance(plugin);
            hooks.add(hook);
            plugin.getLogger().info(pluginName + " live map hook enabled.");
        } catch (ClassNotFoundException ignored) {
            // Map plugin API not available at runtime
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to hook " + pluginName + ": " + e.getMessage());
        }
    }

    public void reload() {
        for (LiveMapHook hook : hooks) {
            hook.reload();
        }
    }

    public void shutdown() {
        for (LiveMapHook hook : hooks) {
            hook.shutdown();
        }
        hooks.clear();
    }
}
