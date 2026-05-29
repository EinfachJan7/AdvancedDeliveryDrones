package de.cb.drones.configeditor;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.drone.DroneSettings;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class ConfigEditorService {
    private final AdvancedDeliveryDronesPlugin plugin;

    public ConfigEditorService(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
    }

    public String getDisplayValue(ConfigOption option) {
        FileConfiguration config = plugin.getConfig();
        return switch (option.type()) {
            case BOOLEAN -> String.valueOf(config.getBoolean(option.configPath()));
            case INT -> String.valueOf(config.getInt(option.configPath()));
            case DOUBLE -> String.valueOf(config.getDouble(option.configPath()));
            case STRING -> truncate(config.getString(option.configPath(), ""), 48);
            case STRING_LIST -> {
                List<String> values = config.getStringList(option.configPath());
                if (values.isEmpty()) {
                    yield "[]";
                }
                yield truncate(String.join(", ", values), 48);
            }
            case ENUM -> config.getString(option.configPath(), option.enumValues().isEmpty() ? "" : option.enumValues().getFirst());
        };
    }

    public boolean applyValue(ConfigOption option, String rawInput) {
        Object parsed = parseValue(option, rawInput);
        if (parsed == null) {
            return false;
        }
        if (option.id().equals("inventory-size") && parsed instanceof Integer size) {
            if (size < 9 || size > 54 || size % 9 != 0) {
                return false;
            }
        }
        plugin.getConfig().set(option.configPath(), parsed);
        plugin.saveConfig();
        reloadRuntime();
        return true;
    }

    public boolean toggleBoolean(ConfigOption option) {
        if (option.type() != ConfigOptionType.BOOLEAN) {
            return false;
        }
        boolean current = plugin.getConfig().getBoolean(option.configPath());
        plugin.getConfig().set(option.configPath(), !current);
        plugin.saveConfig();
        reloadRuntime();
        return true;
    }

    public boolean cycleEnum(ConfigOption option) {
        if (option.type() != ConfigOptionType.ENUM || option.enumValues().isEmpty()) {
            return false;
        }
        String current = plugin.getConfig().getString(option.configPath(), option.enumValues().getFirst());
        List<String> values = option.enumValues();
        int index = values.indexOf(current.toUpperCase(Locale.ROOT));
        if (index < 0) {
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i).equalsIgnoreCase(current)) {
                    index = i;
                    break;
                }
            }
        }
        int next = index < 0 ? 0 : (index + 1) % values.size();
        plugin.getConfig().set(option.configPath(), values.get(next));
        plugin.saveConfig();
        reloadRuntime();
        return true;
    }

    public void reloadRuntime() {
        plugin.reloadPlugin();
    }

    public DroneSettings currentSettings() {
        return plugin.getDroneManager().settings();
    }

    private Object parseValue(ConfigOption option, String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim();
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("abbrechen")) {
            return null;
        }
        return switch (option.type()) {
            case BOOLEAN -> {
                if (input.equalsIgnoreCase("true") || input.equalsIgnoreCase("ja") || input.equals("1")) {
                    yield true;
                }
                if (input.equalsIgnoreCase("false") || input.equalsIgnoreCase("nein") || input.equals("0")) {
                    yield false;
                }
                yield null;
            }
            case INT -> {
                try {
                    yield Integer.parseInt(input);
                } catch (NumberFormatException ignored) {
                    yield null;
                }
            }
            case DOUBLE -> {
                try {
                    yield Double.parseDouble(input.replace(',', '.'));
                } catch (NumberFormatException ignored) {
                    yield null;
                }
            }
            case STRING -> input;
            case STRING_LIST -> {
                if (input.isEmpty() || input.equals("[]")) {
                    yield new ArrayList<String>();
                }
                yield Arrays.stream(input.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toCollection(ArrayList::new));
            }
            case ENUM -> {
                for (String value : option.enumValues()) {
                    if (value.equalsIgnoreCase(input)) {
                        yield value;
                    }
                }
                yield null;
            }
        };
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "—";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }
}
