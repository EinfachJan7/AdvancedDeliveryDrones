package de.cb.drones.command;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.config.DatabaseManager;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class DataConverter {

    public static void convertYamlToMysqlAsync(AdvancedDeliveryDronesPlugin plugin, DatabaseManager tempDb, CommandSender sender) {
        String[] fileNames = {"players", "blacklists", "socket-pending-returns", "sockets", "compose-drafts"};
        String[] dbKeys = {"player_settings", "blacklists", "socket_pending_returns", "sockets", "compose_drafts"};

        for (int i = 0; i < fileNames.length; i++) {
            java.io.File f = new java.io.File(plugin.getDataFolder(), fileNames[i] + ".yml");
            if (f.exists()) {
                YamlConfiguration conf = YamlConfiguration.loadConfiguration(f);
                tempDb.saveConfig(dbKeys[i], conf.saveToString());
            }
        }
        sender.sendMessage(plugin.component("convert-success"));
    }

    public static void convertMysqlToYamlAsync(AdvancedDeliveryDronesPlugin plugin, DatabaseManager oldDb, CommandSender sender) {
        String[] fileNames = {"players", "blacklists", "socket-pending-returns", "sockets", "compose-drafts"};
        String[] dbKeys = {"player_settings", "blacklists", "socket_pending_returns", "sockets", "compose_drafts"};

        for (int i = 0; i < fileNames.length; i++) {
            String data = oldDb.loadConfig(dbKeys[i]);
            if (data != null) {
                java.io.File f = new java.io.File(plugin.getDataFolder(), fileNames[i] + ".yml");
                try {
                    if (!f.exists()) {
                        f.getParentFile().mkdirs();
                        f.createNewFile();
                    }
                    YamlConfiguration conf = new YamlConfiguration();
                    conf.loadFromString(data);
                    conf.save(f);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to convert " + dbKeys[i]);
                }
            }
        }
        sender.sendMessage(plugin.component("convert-success"));
    }

    public static void convertYamlToMysql(AdvancedDeliveryDronesPlugin plugin, CommandSender sender) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || !db.isConnected()) {
            plugin.getConfig().set("database.type", "MYSQL");
            plugin.saveConfig();
            plugin.reloadPlugin(sender);
            db = plugin.getDatabaseManager();
            if (db == null || !db.isConnected()) {
                sender.sendMessage(plugin.componentMessage("convert-error", "<error>", "MySQL could not be connected."));
                return;
            }
        }

        String[] fileNames = {"players", "blacklists", "socket-pending-returns", "sockets", "compose-drafts"};
        String[] dbKeys = {"player_settings", "blacklists", "socket_pending_returns", "sockets", "compose_drafts"};

        for (int i = 0; i < fileNames.length; i++) {
            java.io.File f = new java.io.File(plugin.getDataFolder(), fileNames[i] + ".yml");
            if (f.exists()) {
                YamlConfiguration conf = YamlConfiguration.loadConfiguration(f);
                db.saveConfig(dbKeys[i], conf.saveToString());
            }
        }

        plugin.getConfig().set("database.type", "MYSQL");
        plugin.saveConfig();
        plugin.reloadPlugin(sender);
        sender.sendMessage(plugin.component("convert-success"));
    }

    public static void convertMysqlToYaml(AdvancedDeliveryDronesPlugin plugin, CommandSender sender) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || !db.isConnected()) {
            sender.sendMessage(plugin.componentMessage("convert-error", "<error>", "MySQL is not connected."));
            return;
        }

        String[] fileNames = {"players", "blacklists", "socket-pending-returns", "sockets", "compose-drafts"};
        String[] dbKeys = {"player_settings", "blacklists", "socket_pending_returns", "sockets", "compose_drafts"};

        for (int i = 0; i < fileNames.length; i++) {
            String data = db.loadConfig(dbKeys[i]);
            if (data != null) {
                java.io.File f = new java.io.File(plugin.getDataFolder(), fileNames[i] + ".yml");
                try {
                    if (!f.exists()) {
                        f.getParentFile().mkdirs();
                        f.createNewFile();
                    }
                    YamlConfiguration conf = new YamlConfiguration();
                    conf.loadFromString(data);
                    conf.save(f);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to convert " + dbKeys[i]);
                }
            }
        }

        plugin.getConfig().set("database.type", "YAML");
        plugin.saveConfig();
        plugin.reloadPlugin(sender);
        sender.sendMessage(plugin.component("convert-success"));
    }
}
