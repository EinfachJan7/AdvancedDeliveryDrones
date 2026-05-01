package de.cb.drones.discord;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.drone.DeliveryDrone;
import org.bukkit.entity.Player;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DiscordWebhookManager {
    private final AdvancedDeliveryDronesPlugin plugin;
    private final Gson gson = new Gson();
    private String webhookUrl;
    private boolean enabled;
    private String username;
    private String avatarUrl;
    private boolean embedEnabled;
    private String embedColor;
    private boolean thumbnailEnabled;
    private String thumbnailUrl;
    private boolean imageEnabled;
    private String imageUrl;
    private String footerText;
    private String footerIconUrl;
    private boolean includeItems;
    private boolean includeAnimals;
    private int maxItemsDisplay;
    private int maxAnimalsDisplay;
    
    public DiscordWebhookManager(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
        loadSettings();
    }
    
    public void loadSettings() {
        this.webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        this.enabled = plugin.getConfig().getBoolean("discord.enabled", true) && !webhookUrl.isBlank();
        this.username = plugin.getConfig().getString("discord.username", "Delivery Drone");
        this.avatarUrl = plugin.getConfig().getString("discord.avatar-url", "");
        this.embedEnabled = plugin.getConfig().getBoolean("discord.embed.enabled", true);
        this.embedColor = plugin.getConfig().getString("discord.embed.color", "#00ff00");
        this.thumbnailEnabled = plugin.getConfig().getBoolean("discord.embed.thumbnail.enabled", true);
        this.thumbnailUrl = plugin.getConfig().getString("discord.embed.thumbnail.url", "");
        this.imageEnabled = plugin.getConfig().getBoolean("discord.embed.image.enabled", false);
        this.imageUrl = plugin.getConfig().getString("discord.embed.image.url", "");
        this.footerText = plugin.getConfig().getString("discord.embed.footer.text", "Advanced Delivery Drones");
        this.footerIconUrl = plugin.getConfig().getString("discord.embed.footer.icon-url", "");
        this.includeItems = plugin.getConfig().getBoolean("discord.include-items", true);
        this.includeAnimals = plugin.getConfig().getBoolean("discord.include-animals", true);
        this.maxItemsDisplay = plugin.getConfig().getInt("discord.max-items-display", 10);
        this.maxAnimalsDisplay = plugin.getConfig().getInt("discord.max-animals-display", 5);
        
        if (!enabled) {
            plugin.getLogger().info("Discord webhook is disabled (no webhook URL configured)");
        } else {
            plugin.getLogger().info("Discord webhook integration enabled");
        }
    }
    
    public void sendDeliveryNotification(Player sender, Player receiver, DeliveryDrone drone) {
        if (!enabled) return;
        
        String title = "📦 Drone Delivery Started";
        String description = String.format(
            "**From:** %s → **To:** %s\n" +
            "**ETA:** %d seconds",
            sender.getName(),
            receiver.getName(),
            (int)(drone.currentLocation().distance(receiver.getLocation()) / 5.0)
        );
        
        // Add items and animals information
        StringBuilder details = new StringBuilder();
        
        if (includeItems && !isInventoryEmpty(drone.inventory())) {
            details.append("**Items:** ");
            org.bukkit.inventory.ItemStack[] contents = drone.inventory().getContents();
            int itemCount = 0;
            for (org.bukkit.inventory.ItemStack item : contents) {
                if (item != null && !item.getType().isAir() && itemCount < maxItemsDisplay) {
                    if (itemCount > 0) details.append(", ");
                    details.append(formatItem(item));
                    itemCount++;
                }
            }
            if (itemCount >= maxItemsDisplay) {
                details.append("...");
            }
            details.append("\n");
        }
        
        if (includeAnimals && !drone.attachedAnimalTypes().isEmpty()) {
            details.append("**Animals:** ");
            int animalCount = 0;
            for (org.bukkit.entity.EntityType animalType : drone.attachedAnimalTypes()) {
                if (animalCount > 0) details.append(", ");
                details.append(formatAnimalType(animalType));
                animalCount++;
                if (animalCount >= maxAnimalsDisplay) break;
            }
            if (animalCount >= maxAnimalsDisplay) {
                details.append("...");
            }
            details.append("\n");
        }
        
        if (details.length() > 0) {
            description += "\n" + details.toString();
        }
        
        sendMessage(title, description, sender, receiver);
    }
    
    public void sendDeliveryCompleted(Player sender, Player receiver, DeliveryDrone drone) {
        if (!enabled) return;
        
        String title = "✅ Drone Delivered";
        String description = String.format(
            "**From:** %s ➜ **To:** %s\n" +
            "**Status:** Successfully delivered\n" +
            "**ID:** `%s`",
            sender.getName(),
            receiver.getName(),
            drone.droneId().toString().substring(0, 8)
        );
        
        sendMessage(title, description, sender, receiver);
    }
    
    public void sendDeliveryDeclined(Player sender, Player receiver, DeliveryDrone drone) {
        if (!enabled) return;
        
        String title = "❌ Drone Declined";
        String description = String.format(
            "**From:** %s ➜ **To:** %s\n" +
            "**Status:** Declined by receiver\n" +
            "**ID:** `%s`",
            sender.getName(),
            receiver.getName(),
            drone.droneId().toString().substring(0, 8)
        );
        
        sendMessage(title, description, sender, receiver);
    }
    
    public void sendDeliveryExpired(Player sender, Player receiver, DeliveryDrone drone) {
        if (!enabled) return;
        
        String title = "⏰ Drone Expired";
        String description = String.format(
            "**From:** %s ➜ **To:** %s\n" +
            "**Status:** Time limit exceeded\n" +
            "**ID:** `%s`",
            sender.getName(),
            receiver.getName(),
            drone.droneId().toString().substring(0, 8)
        );
        
        sendMessage(title, description, sender, receiver);
    }
    
    private void sendMessage(String title, String description, Player sender, Player receiver) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("username", username);
                if (!avatarUrl.isBlank()) {
                    payload.addProperty("avatar_url", avatarUrl);
                }
                
                if (embedEnabled) {
                    JsonObject embed = new JsonObject();
                    embed.addProperty("title", title);
                    embed.addProperty("description", description);
                    embed.addProperty("color", parseColor(embedColor));
                    
                    // Add timestamp
                    embed.addProperty("timestamp", java.time.Instant.now().toString());
                    
                    // Add thumbnail
                    if (thumbnailEnabled && !thumbnailUrl.isBlank()) {
                        JsonObject thumbnail = new JsonObject();
                        thumbnail.addProperty("url", thumbnailUrl);
                        embed.add("thumbnail", thumbnail);
                    }
                    
                    // Add image
                    if (imageEnabled && !imageUrl.isBlank()) {
                        JsonObject image = new JsonObject();
                        image.addProperty("url", imageUrl);
                        embed.add("image", image);
                    }
                    
                    // Add footer
                    JsonObject footer = new JsonObject();
                    footer.addProperty("text", footerText);
                    if (!footerIconUrl.isBlank()) {
                        footer.addProperty("icon_url", footerIconUrl);
                    }
                    embed.add("footer", footer);
                    
                    // Add author (sender)
                    JsonObject author = new JsonObject();
                    author.addProperty("name", sender.getName());
                    embed.add("author", author);
                    
                    JsonArray embeds = new JsonArray();
                    embeds.add(embed);
                    payload.add("embeds", embeds);
                } else {
                    // Fallback to simple message
                    payload.addProperty("content", "**" + title + "**\n" + description);
                }
                
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "AdvancedDeliveryDrones/1.0");
                connection.setDoOutput(true);
                
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = connection.getResponseCode();
                if (responseCode != 204) {
                    plugin.getLogger().warning("Discord webhook returned response code: " + responseCode);
                }
                
                connection.disconnect();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send Discord webhook message", e);
            }
        });
    }
    
    private boolean isInventoryEmpty(org.bukkit.inventory.Inventory inventory) {
        for (org.bukkit.inventory.ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
    
    private String formatItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) return "Air";
        String name = item.getType().name().toLowerCase().replace("_", " ");
        if (item.getAmount() > 1) {
            name += " x" + item.getAmount();
        }
        return name;
    }
    
    private String formatAnimalType(org.bukkit.entity.EntityType entityType) {
        switch (entityType) {
            case COW: return "Cow";
            case PIG: return "Pig";
            case SHEEP: return "Sheep";
            case CHICKEN: return "Chicken";
            case HORSE: return "Horse";
            case DONKEY: return "Donkey";
            case MULE: return "Mule";
            case WOLF: return "Wolf";
            case CAT: return "Cat";
            case OCELOT: return "Ocelot";
            case RABBIT: return "Rabbit";
            case LLAMA: return "Llama";
            case PARROT: return "Parrot";
            case TURTLE: return "Turtle";
            case FOX: return "Fox";
            case PANDA: return "Panda";
            default: return entityType.name().toLowerCase().replace("_", " ");
        }
    }
    
    private int parseColor(String hexColor) {
        try {
            if (hexColor.startsWith("#")) {
                return Integer.parseInt(hexColor.substring(1), 16);
            }
            return 0x00ff00; // Default green
        } catch (NumberFormatException e) {
            return 0x00ff00; // Default green on error
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public String getWebhookUrl() {
        return webhookUrl;
    }
}
