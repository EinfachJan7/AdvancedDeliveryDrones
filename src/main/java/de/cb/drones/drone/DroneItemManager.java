package de.cb.drones.drone;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DroneItemManager {

    private final AdvancedDeliveryDronesPlugin plugin;
    private final NamespacedKey recipeKey;
    private final NamespacedKey itemMarkerKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private boolean requireItem;
    private ItemStack droneItem;

    public DroneItemManager(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "drone_item_recipe");
        this.itemMarkerKey = new NamespacedKey(plugin, "is_drone_item");
        loadConfig();
    }

    public void loadConfig() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("settings.drone.drone-item");
        if (section == null) {
            this.requireItem = false;
            return;
        }

        this.requireItem = section.getBoolean("require-item", false);
        
        // Build the item
        String materialName = section.getString("item.material", "ELYTRA");
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.ELYTRA;

        this.droneItem = new ItemStack(material);
        ItemMeta meta = this.droneItem.getItemMeta();
        if (meta != null) {
            String name = section.getString("item.name", "<yellow>Delivery Drone");
            meta.displayName(miniMessage.deserialize(name));

            List<String> loreStr = section.getStringList("item.lore");
            if (!loreStr.isEmpty()) {
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                for (String l : loreStr) {
                    lore.add(miniMessage.deserialize(l));
                }
                meta.lore(lore);
            }

            int customModelData = section.getInt("item.custom-model-data", 0);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            if (material == Material.PLAYER_HEAD && meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
                String headTexture = section.getString("item.head-texture", "");
                if (headTexture != null && !headTexture.trim().isEmpty()) {
                    de.cb.drones.util.SkullTextureUtils.applyTexture(skullMeta, headTexture.trim());
                }
            }

            // Mark the item so we can identify it securely
            meta.getPersistentDataContainer().set(itemMarkerKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            
            this.droneItem.setItemMeta(meta);
        }

        // Unregister existing recipe if any
        Bukkit.removeRecipe(recipeKey);

        if (section.getBoolean("crafting.enabled", true)) {
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, this.droneItem);
            List<String> shapeList = section.getStringList("crafting.shape");
            if (shapeList.size() == 3) {
                // Validate shape is not empty
                boolean validShape = false;
                for (String row : shapeList) {
                    if (row != null && !row.trim().isEmpty()) {
                        validShape = true;
                        break;
                    }
                }

                if (!validShape) {
                    plugin.getLogger().warning("Drone item crafting shape is invalid (empty)! Crafting disabled.");
                    return;
                }

                try {
                    recipe.shape(shapeList.get(0), shapeList.get(1), shapeList.get(2));
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to set recipe shape! Check your shape in config.yml.");
                    return;
                }

                ConfigurationSection ingredients = section.getConfigurationSection("crafting.ingredients");
                boolean hasIngredients = false;
                if (ingredients != null) {
                    for (String key : ingredients.getKeys(false)) {
                        if (key.length() == 1) {
                            char ingredientChar = key.charAt(0);
                            boolean charInShape = false;
                            for (String row : shapeList) {
                                if (row.indexOf(ingredientChar) != -1) {
                                    charInShape = true;
                                    break;
                                }
                            }
                            
                            if (charInShape) {
                                String ingMatName = ingredients.getString(key);
                                Material ingMat = Material.matchMaterial(ingMatName != null ? ingMatName : "AIR");
                                if (ingMat != null && ingMat != Material.AIR) {
                                    recipe.setIngredient(ingredientChar, ingMat);
                                    hasIngredients = true;
                                }
                            }
                        }
                    }
                    
                    if (hasIngredients) {
                        try {
                            Bukkit.addRecipe(recipe);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to register drone item crafting recipe! Check your shape/ingredients in config.yml.");
                        }
                    } else {
                        plugin.getLogger().warning("No valid ingredients found for drone item crafting! Crafting disabled.");
                    }
                }
            } else {
                plugin.getLogger().warning("Drone item crafting shape must have exactly 3 rows! Crafting disabled.");
            }
        }
    }

    public boolean isRequireItem() {
        return requireItem;
    }

    public ItemStack getDroneItem() {
        return droneItem != null ? droneItem.clone() : null;
    }

    public boolean hasAndConsumeDroneItem(Player player) {
        if (!requireItem) {
            return true;
        }
        
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (isDroneItem(item)) {
                item.setAmount(item.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    private boolean isDroneItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(itemMarkerKey, org.bukkit.persistence.PersistentDataType.BYTE);
    }
}
