package dev.abdelrahman.rankcorex.managers;

import dev.abdelrahman.rankcorex.Rankcorex;
import dev.abdelrahman.rankcorex.models.PlayerRankData;
import dev.abdelrahman.rankcorex.models.RankData;
import dev.abdelrahman.rankcorex.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class StorageManager {

    private final Rankcorex plugin;
    private final String storageType;
    private Connection connection;
    private File playersFile;
    private FileConfiguration playersConfig;

    public StorageManager(Rankcorex plugin) {
        this.plugin = plugin;
        this.storageType = plugin.getConfig().getString("storage.type", "yaml").toLowerCase();
    }

    public boolean initialize() {
        plugin.debug("Initializing storage manager with type: " + storageType);

        if (storageType.equals("mysql")) {
            return initializeMySQL();
        } else {
            return initializeYAML();
        }
    }

    private boolean initializeYAML() {
        try {
            playersFile = new File(plugin.getDataFolder(), "players.yml");
            if (!playersFile.exists()) {
                playersFile.createNewFile();
            }
            playersConfig = YamlConfiguration.loadConfiguration(playersFile);
            plugin.debug("YAML storage initialized successfully");
            return true;
        } catch (Exception e) {
            plugin.error("Failed to initialize YAML storage: " + e.getMessage());
            return false;
        }
    }

    private boolean initializeMySQL() {
        try {
            String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String database = plugin.getConfig().getString("storage.mysql.database", "rankcorex");
            String username = plugin.getConfig().getString("storage.mysql.username", "root");
            String password = plugin.getConfig().getString("storage.mysql.password", "password");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true&useSSL=false";
            connection = DriverManager.getConnection(url, username, password);

            createTables();
            plugin.debug("MySQL storage initialized successfully");
            return true;
        } catch (SQLException e) {
            plugin.error("Failed to initialize MySQL storage: " + e.getMessage());
            return false;
        }
    }

    private void createTables() throws SQLException {
        String playersTable = "CREATE TABLE IF NOT EXISTS rankcorex_players (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "username VARCHAR(16) NOT NULL, " +
                "rank_name VARCHAR(50) NOT NULL, " +
                "time_given VARCHAR(20) NOT NULL, " +
                "time_expires VARCHAR(20) NULL" +
                ")";

        try (PreparedStatement stmt = connection.prepareStatement(playersTable)) {
            stmt.executeUpdate();
        }

        plugin.debug("MySQL tables created/verified");
    }

    public CompletableFuture<PlayerRankData> getPlayerRank(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            if (storageType.equals("mysql")) {
                return getPlayerRankMySQL(playerId);
            } else {
                return getPlayerRankYAML(playerId);
            }
        });
    }

    private PlayerRankData getPlayerRankMySQL(UUID playerId) {
        try {
            String query = "SELECT * FROM rankcorex_players WHERE uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String username = rs.getString("username");
                    String rankName = rs.getString("rank_name");
                    String timeGiven = rs.getString("time_given");
                    String timeExpires = rs.getString("time_expires");

                    PlayerRankData data = new PlayerRankData(playerId, username, rankName, timeGiven, timeExpires);

                    // Check if expired
                    if (data.hasExpired()) {
                        removePlayerRankMySQL(playerId);
                        return null; // Will fall back to default rank
                    }

                    return data;
                }
            }
        } catch (SQLException e) {
            plugin.error("Failed to get player rank from MySQL: " + e.getMessage());
        }
        return null;
    }

    private PlayerRankData getPlayerRankYAML(UUID playerId) {
        try {
            ConfigurationSection playerSection = playersConfig.getConfigurationSection("players." + playerId.toString());
            if (playerSection != null) {
                String username = playerSection.getString("username");
                String rankName = playerSection.getString("rank");
                String timeGiven = playerSection.getString("given");
                String timeExpires = playerSection.getString("expires");

                PlayerRankData data = new PlayerRankData(playerId, username, rankName, timeGiven, timeExpires);

                // Check if expired
                if (data.hasExpired()) {
                    removePlayerRankYAML(playerId);
                    return null; // Will fall back to default rank
                }

                return data;
            }
        } catch (Exception e) {
            plugin.error("Failed to get player rank from YAML: " + e.getMessage());
        }
        return null;
    }

    public CompletableFuture<Void> setPlayerRank(UUID playerId, String username, String rankName, String timeExpires) {
        return CompletableFuture.runAsync(() -> {
            String timeGiven = TimeUtils.getCurrentTimestamp();

            if (storageType.equals("mysql")) {
                setPlayerRankMySQL(playerId, username, rankName, timeGiven, timeExpires);
            } else {
                setPlayerRankYAML(playerId, username, rankName, timeGiven, timeExpires);
            }
        });
    }

    private void setPlayerRankMySQL(UUID playerId, String username, String rankName, String timeGiven, String timeExpires) {
        try {
            String query = "REPLACE INTO rankcorex_players (uuid, username, rank_name, time_given, time_expires) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, username);
                stmt.setString(3, rankName);
                stmt.setString(4, timeGiven);
                stmt.setString(5, timeExpires);
                stmt.executeUpdate();
            }
            plugin.debug("Set rank for " + username + " to " + rankName + " in MySQL");
        } catch (SQLException e) {
            plugin.error("Failed to set player rank in MySQL: " + e.getMessage());
        }
    }

    private void setPlayerRankYAML(UUID playerId, String username, String rankName, String timeGiven, String timeExpires) {
        try {
            String path = "players." + playerId.toString();
            playersConfig.set(path + ".username", username);
            playersConfig.set(path + ".rank", rankName);
            playersConfig.set(path + ".given", timeGiven);
            playersConfig.set(path + ".expires", timeExpires);

            playersConfig.save(playersFile);
            plugin.debug("Set rank for " + username + " to " + rankName + " in YAML");
        } catch (Exception e) {
            plugin.error("Failed to set player rank in YAML: " + e.getMessage());
        }
    }

    public CompletableFuture<Void> removePlayerRank(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            if (storageType.equals("mysql")) {
                removePlayerRankMySQL(playerId);
            } else {
                removePlayerRankYAML(playerId);
            }
        });
    }

    private void removePlayerRankMySQL(UUID playerId) {
        try {
            String query = "DELETE FROM rankcorex_players WHERE uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerId.toString());
                stmt.executeUpdate();
            }
            plugin.debug("Removed rank for player " + playerId + " from MySQL");
        } catch (SQLException e) {
            plugin.error("Failed to remove player rank from MySQL: " + e.getMessage());
        }
    }

    private void removePlayerRankYAML(UUID playerId) {
        try {
            playersConfig.set("players." + playerId.toString(), null);
            playersConfig.save(playersFile);
            plugin.debug("Removed rank for player " + playerId + " from YAML");
        } catch (Exception e) {
            plugin.error("Failed to remove player rank from YAML: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (connection != null) {
            try {
                connection.close();
                plugin.debug("MySQL connection closed");
            } catch (SQLException e) {
                plugin.error("Failed to close MySQL connection: " + e.getMessage());
            }
        }
    }
}