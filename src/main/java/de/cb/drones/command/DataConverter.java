package de.cb.drones.command;

import de.cb.drones.AdvancedDeliveryDronesPlugin;
import de.cb.drones.config.DatabaseManager;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class DataConverter {

    public static void convertYamlToMysql(AdvancedDeliveryDronesPlugin plugin, CommandSender sender) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || !db.isConnected()) {
            plugin.getConfig().set("database.type", "MYSQL");
            plugin.saveConfig();
            plugin.reloadPlugin();
            db = plugin.getDatabaseManager();
            if (db == null || !db.isConnected()) {
                sender.sendMessage(plugin.componentMessage("convert-error", "<error>", "MySQL could not be connected."));
                return;
            }
        }

        String[] fileNames = {"players", "blacklists", "socket-pending-returns", "sockets"};
        String[] dbKeys = {"player_settings", "blacklists", "socket_pending_returns", "sockets"};

        for (int i = 0; i < fileNames.length; i++) {
            File f = new File(plugin.getDataFolder(), fileNames[i] + ".yml");
            if (f.exists()) {
                YamlConfiguration conf = YamlConfiguration.loadConfiguration(f);
                db.saveConfig(dbKeys[i], conf.saveToString());
                // The file will be deleted by the repository reload()
            }
        }

        plugin.getConfig().set("database.type", "MYSQL");
        plugin.saveConfig();
        plugin.reloadPlugin();
        sender.sendMessage(plugin.component("convert-success"));
    }

    public static void convertMysqlToYaml(AdvancedDeliveryDronesPlugin plugin, CommandSender sender) {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null || !db.isConnected()) {
            sender.sendMessage(plugin.componentMessage("convert-error", "<error>", "MySQL is not connected."));
            return;
        }

        String[] fileNames = {"players", "blacklists", "socket-pending-returns", "sockets"};
        String[] dbKeys = {"player_settings", "blacklists", "socket_pending_returns", "sockets"};

        for (int i = 0; i < fileNames.length; i++) {
            String data = db.loadConfig(dbKeys[i]);
            if (data != null) {
                File f = new File(plugin.getDataFolder(), fileNames[i] + ".yml");
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
        plugin.reloadPlugin();
        sender.sendMessage(plugin.component("convert-success"));
    }
}
