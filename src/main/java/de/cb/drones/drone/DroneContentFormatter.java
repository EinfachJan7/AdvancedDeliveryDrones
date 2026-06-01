package de.cb.drones.drone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

final class DroneContentFormatter {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private DroneContentFormatter() {
    }

    static int totalItemAmount(ItemStack[] contents) {
        int total = 0;
        if (contents == null) {
            return 0;
        }
        for (ItemStack stack : contents) {
            if (stack != null && !stack.getType().isAir()) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    static String formatItemsSummary(ItemStack[] contents) {
        return formatItemsSummary(contents, ", ");
    }

    static String formatItemsSummary(ItemStack[] contents, String separator) {
        Map<String, Integer> grouped = groupItems(contents);
        if (grouped.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : grouped.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(separator);
            }
            builder.append(entry.getValue()).append("x ").append(entry.getKey());
        }
        return builder.toString();
    }

    static String formatItemsList(ItemStack[] contents) {
        return formatItemsList(contents, ", ");
    }

    static String formatItemsList(ItemStack[] contents, String separator) {
        if (contents == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            parts.add(stack.getAmount() + "x " + formatItemName(stack));
        }
        return String.join(separator, parts);
    }

    static String formatAnimalsSummary(List<EntityType> animalTypes) {
        return formatAnimalsSummary(animalTypes, ", ");
    }

    static String formatAnimalsSummary(List<EntityType> animalTypes, String separator) {
        if (animalTypes == null || animalTypes.isEmpty()) {
            return "";
        }
        Map<String, Integer> grouped = new LinkedHashMap<>();
        for (EntityType type : animalTypes) {
            String name = formatEntityType(type);
            grouped.merge(name, 1, Integer::sum);
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : grouped.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(separator);
            }
            builder.append(entry.getValue()).append("x ").append(entry.getKey());
        }
        return builder.toString();
    }

    static String formatAnimalsList(List<EntityType> animalTypes) {
        return formatAnimalsList(animalTypes, ", ");
    }

    static String formatAnimalsList(List<EntityType> animalTypes, String separator) {
        if (animalTypes == null || animalTypes.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>(animalTypes.size());
        for (EntityType type : animalTypes) {
            parts.add(formatEntityType(type));
        }
        return String.join(separator, parts);
    }

    static ItemStack getInventoryItemAt(ItemStack[] contents, int oneBasedIndex) {
        if (contents == null || oneBasedIndex < 1) {
            return null;
        }
        int seen = 0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            seen++;
            if (seen == oneBasedIndex) {
                return stack;
            }
        }
        return null;
    }

    static EntityType getAnimalTypeAt(List<EntityType> animalTypes, int oneBasedIndex) {
        if (animalTypes == null || oneBasedIndex < 1 || oneBasedIndex > animalTypes.size()) {
            return null;
        }
        return animalTypes.get(oneBasedIndex - 1);
    }

    private static Map<String, Integer> groupItems(ItemStack[] contents) {
        Map<String, Integer> grouped = new LinkedHashMap<>();
        if (contents == null) {
            return grouped;
        }
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            String name = formatItemName(stack);
            grouped.merge(name, stack.getAmount(), Integer::sum);
        }
        return grouped;
    }

    private static String formatItemName(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return PLAIN.serialize(stack.getItemMeta().displayName());
        }
        String key = stack.getType().getKey().getKey().replace('_', ' ');
        if (key.isEmpty()) {
            return stack.getType().name();
        }
        return key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
    }

    private static String formatEntityType(EntityType type) {
        if (type == null) {
            return "";
        }
        String key = type.getKey().getKey().replace('_', ' ');
        if (key.isEmpty()) {
            return type.name();
        }
        return key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
    }
}
