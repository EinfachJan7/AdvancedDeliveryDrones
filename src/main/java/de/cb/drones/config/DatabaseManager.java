package de.cb.drones.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.cb.drones.AdvancedDeliveryDronesPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
            createStorageTable();
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

    private void createStorageTable() {
        if (!isConnected()) return;
        String query = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "storage (" +
                "data_key VARCHAR(64) PRIMARY KEY, " +
                "data_value LONGTEXT NOT NULL" +
                ");";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(query);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create MySQL storage table!");
            e.printStackTrace();
        }
    }

    public void saveConfig(String key, String data) {
        if (!isConnected()) return;
        String query = "REPLACE INTO " + tablePrefix + "storage (data_key, data_value) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, key);
            pstmt.setString(2, data);
            pstmt.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save config " + key + " to MySQL!");
            e.printStackTrace();
        }
    }

    public String loadConfig(String key) {
        if (!isConnected()) return null;
        String query = "SELECT data_value FROM " + tablePrefix + "storage WHERE data_key = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("data_value");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load config " + key + " from MySQL!");
            e.printStackTrace();
        }
        return null;
    }

    public void deleteConfig(String key) {
        if (!isConnected()) return;
        String query = "DELETE FROM " + tablePrefix + "storage WHERE data_key = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, key);
            pstmt.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to delete config " + key + " from MySQL!");
            e.printStackTrace();
        }
    }
}
