package dev.abdelrahman.rankcorex;

import dev.abdelrahman.rankcorex.commands.RankCommand;
import dev.abdelrahman.rankcorex.listeners.JoinListener;
import dev.abdelrahman.rankcorex.managers.RankManager;
import dev.abdelrahman.rankcorex.managers.StorageManager;
import dev.abdelrahman.rankcorex.managers.SyncManager;
import dev.abdelrahman.rankcorex.placeholder.RankExpansion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Rankcorex extends JavaPlugin {

    private static Rankcorex instance;
    private StorageManager storageManager;
    private RankManager rankManager;
    private SyncManager syncManager;
    private RankExpansion placeholderExpansion;

    private boolean debugMode;

    @Override
    public void onEnable() {
        instance = this;

        // Save default configurations
        saveDefaultConfig();
        saveResource("ranks.yml", false);

        // Initialize debug mode
        debugMode = getConfig().getBoolean("debug", false);

        // Initialize storage FIRST
        storageManager = new StorageManager(this);
        if (!storageManager.initialize()) {
            getLogger().severe("Failed to initialize storage! Plugin will be disabled.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize rank manager and load ranks
        rankManager = new RankManager(this);
        rankManager.loadRanks();

        // Initialize sync manager
        syncManager = new SyncManager(this);
        if (getConfig().getBoolean("global-sync", false)) {
            if (!syncManager.initialize()) {
                getLogger().warning("Failed to initialize sync manager!");
            }
        }

        // Register commands
        getCommand("rank").setExecutor(new RankCommand(this));

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new JoinListener(this), this);

        // Register PlaceholderAPI hook AFTER everything is loaded
        registerPlaceholders();

        log("RankCorex v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }

        if (syncManager != null) {
            syncManager.shutdown();
        }

        if (storageManager != null) {
            storageManager.shutdown();
        }

        log("RankCorex has been disabled!");
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new RankExpansion(this);
            if (placeholderExpansion.register()) {
                log("PlaceholderAPI integration enabled!");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion!");
            }
        } else {
            getLogger().warning("PlaceholderAPI not found! Rank placeholders will not work.");
        }
    }

    public void log(String message) {
        getLogger().info(message);
    }

    public void debug(String message) {
        if (debugMode) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public void error(String message) {
        getLogger().severe(message);
    }

    public static Rankcorex getInstance() {
        return instance;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }

    public boolean isDebugMode() {
        return debugMode;
    }
}