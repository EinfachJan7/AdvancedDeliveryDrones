package de.cb.drones.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.cb.drones.AdvancedDeliveryDronesPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    private final AdvancedDeliveryDronesPlugin plugin;
    private HikariDataSource dataSource;
    private String tablePrefix;

    public DatabaseManager(AdvancedDeliveryDronesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("database.mysql");
        if (dbConfig == null) {
            plugin.getLogger().severe("MySQL configuration is missing!");
            return false;
        }

        String host = dbConfig.getString("host", "localhost");
        int port = dbConfig.getInt("port", 3306);
        String database = dbConfig.getString("database", "deliverydrones");
        String username = dbConfig.getString("username", "root");
        String password = dbConfig.getString("password", "");
        this.tablePrefix = dbConfig.getString("table-prefix", "add_");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true");
        config.setUsername(username);
        config.setPassword(password);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);
        config.setPoolName("AdvancedDeliveryDrones-MySQL-Pool");

        try {
            this.dataSource = new HikariDataSource(config);
            plugin.getLogger().info("Successfully connected to MySQL database.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to MySQL database!");
            e.printStackTrace();
            return false;
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL connection closed.");
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is null (not connected)");
        }
        return dataSource.getConnection();
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}
