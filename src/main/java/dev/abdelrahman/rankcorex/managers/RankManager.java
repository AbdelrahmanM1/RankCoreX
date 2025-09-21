package dev.abdelrahman.rankcorex.managers;

import dev.abdelrahman.rankcorex.Rankcorex;
import dev.abdelrahman.rankcorex.models.PlayerRankData;
import dev.abdelrahman.rankcorex.models.RankData;
import dev.abdelrahman.rankcorex.utils.MessageUtils;
import dev.abdelrahman.rankcorex.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class RankManager {

    private final Rankcorex plugin;
    private final Map<String, RankData> ranks = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerRankData> playerRanks = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> permissionAttachments = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerPermissions = new ConcurrentHashMap<>();

    // Thread safety for critical operations
    private final ReentrantLock rankLoadLock = new ReentrantLock();
    private final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

    private RankData defaultRank;

    // Constants for validation
    private static final String PERMISSION_PATTERN = "^[a-zA-Z0-9._*-]+$";
    private static final int MAX_PREFIX_LENGTH = 16;
    private static final int MAX_SUFFIX_LENGTH = 16;
    private static final String TEAM_PREFIX = "rc_";

    public RankManager(Rankcorex plugin) {
        this.plugin = plugin;
    }

    public void loadRanks() {
        rankLoadLock.lock();
        try {
            ranks.clear();
            File ranksFile = new File(plugin.getDataFolder(), "ranks.yml");

            if (!ranksFile.exists()) {
                plugin.error("ranks.yml not found! Creating default...");
                plugin.saveResource("ranks.yml", false);
            }

            FileConfiguration ranksConfig = YamlConfiguration.loadConfiguration(ranksFile);
            ConfigurationSection ranksSection = ranksConfig.getConfigurationSection("ranks");

            if (ranksSection == null) {
                plugin.error("No ranks section found in ranks.yml!");
                return;
            }

            // Track default ranks for validation
            List<String> defaultRanks = new ArrayList<>();

            for (String rankName : ranksSection.getKeys(false)) {
                ConfigurationSection rankSection = ranksSection.getConfigurationSection(rankName);
                if (rankSection == null) continue;

                if (!isValidRankName(rankName)) {
                    plugin.error("Invalid rank name: " + rankName + " - must only contain alphanumeric characters, dots, underscores, and hyphens");
                    continue;
                }

                String prefix = sanitizeString(rankSection.getString("prefix", ""));
                String suffix = sanitizeString(rankSection.getString("suffix", ""));
                int weight = Math.max(1, rankSection.getInt("weight", 1)); // Ensure positive weight
                boolean isDefault = rankSection.getBoolean("default", false);
                List<String> permissions = rankSection.getStringList("permissions");

                // Validate and clean permissions
                permissions = validatePermissions(permissions, rankName);

                RankData rankData = new RankData(rankName, prefix, suffix, weight, isDefault, permissions);
                ranks.put(rankName.toLowerCase(), rankData);

                if (isDefault) {
                    defaultRanks.add(rankName);
                    defaultRank = rankData;
                }

                plugin.debug("Loaded rank: " + rankName + " (weight: " + weight + ", default: " + isDefault + ", permissions: " + permissions.size() + ")");
            }

            // Validate default rank configuration
            if (defaultRanks.size() > 1) {
                plugin.error("Multiple default ranks found: " + String.join(", ", defaultRanks) + ". Using: " + defaultRank.getName());
            } else if (defaultRanks.isEmpty()) {
                plugin.error("No default rank found! Please set one rank with 'default: true' in ranks.yml");
                // Create emergency default rank
                createEmergencyDefaultRank();
            }

            plugin.log("Loaded " + ranks.size() + " ranks from configuration");
        } finally {
            rankLoadLock.unlock();
        }
    }

    /**
     * Create an emergency default rank if none is configured
     */
    private void createEmergencyDefaultRank() {
        plugin.log("Creating emergency default rank...");
        RankData emergencyRank = new RankData("default", "&7[Default] ", "", 1, true, new ArrayList<>());
        ranks.put("default", emergencyRank);
        defaultRank = emergencyRank;
    }

    /**
     * Sanitize configuration strings
     */
    private String sanitizeString(String input) {
        return input == null ? "" : input.trim();
    }

    /**
     * Validate rank name format
     */
    private boolean isValidRankName(String rankName) {
        return rankName != null &&
                !rankName.trim().isEmpty() &&
                rankName.matches("^[a-zA-Z0-9._-]+$") &&
                rankName.length() <= 32;
    }

    /**
     * permission validation with better error reporting
     */
    private List<String> validatePermissions(List<String> permissions, String rankName) {
        List<String> validPermissions = new ArrayList<>();
        Set<String> seenPermissions = new HashSet<>();

        for (String permission : permissions) {
            if (permission == null || permission.trim().isEmpty()) {
                continue;
            }

            String cleanPerm = permission.trim();

            // Check for duplicates
            if (seenPermissions.contains(cleanPerm.toLowerCase())) {
                plugin.error("Duplicate permission in rank " + rankName + ": " + permission);
                continue;
            }
            seenPermissions.add(cleanPerm.toLowerCase());

            // Handle negative permissions
            if (cleanPerm.startsWith("-")) {
                String actualPerm = cleanPerm.substring(1).trim();
                if (actualPerm.isEmpty()) {
                    plugin.error("Invalid negative permission in rank " + rankName + ": " + permission);
                    continue;
                }
                if (isValidPermission(actualPerm)) {
                    validPermissions.add(cleanPerm);
                } else {
                    plugin.error("Invalid negative permission format in rank " + rankName + ": " + permission);
                }
            } else {
                // Validate positive permission format
                if (isValidPermission(cleanPerm)) {
                    validPermissions.add(cleanPerm);
                } else {
                    plugin.error("Invalid permission format in rank " + rankName + ": " + permission);
                }
            }
        }

        return validPermissions;
    }

    /**
     * permission format validation
     */
    private boolean isValidPermission(String permission) {
        if (permission == null || permission.isEmpty()) {
            return false;
        }

        // Allow wildcard
        if (permission.equals("*")) {
            return true;
        }

        // Check basic format and length
        if (permission.length() > 100) {
            return false;
        }

        // Improved validation - allow more common permission patterns
        return permission.matches(PERMISSION_PATTERN) &&
                !permission.startsWith(".") &&
                !permission.endsWith(".");
    }

    // Thread-safe player lock management
    private ReentrantLock getPlayerLock(UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, k -> new ReentrantLock());
    }

    public RankData getRank(String rankName) {
        return ranks.get(rankName.toLowerCase());
    }

    public Collection<RankData> getAllRanks() {
        return new ArrayList<>(ranks.values());
    }

    public List<RankData> getAllRanksSorted() {
        return ranks.values().stream()
                .sorted((r1, r2) -> Integer.compare(r2.getWeight(), r1.getWeight()))
                .collect(Collectors.toList());
    }

    public RankData getDefaultRank() {
        return defaultRank;
    }

    public boolean rankExists(String rankName) {
        return rankName != null && ranks.containsKey(rankName.toLowerCase());
    }

    public PlayerRankData getPlayerRank(UUID playerId) {
        return playerRanks.get(playerId);
    }

    /**
     * Enhanced player rank loading with better error handling
     */
    public void loadPlayerRank(Player player) {
        if (player == null) {
            plugin.error("Attempted to load rank for null player");
            return;
        }

        UUID playerId = player.getUniqueId();
        ReentrantLock lock = getPlayerLock(playerId);

        plugin.getStorageManager().getPlayerRank(playerId).thenAccept(rankData -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                lock.lock();
                try {
                    // Verify player is still online
                    if (!player.isOnline()) {
                        plugin.debug("Player " + player.getName() + " went offline during rank loading");
                        return;
                    }

                    if (rankData != null && rankExists(rankData.getRankName())) {
                        // Check if rank has expired
                        if (rankData.getTimeExpires() != null && TimeUtils.hasExpired(rankData.getTimeExpires())) {
                            plugin.debug("Rank " + rankData.getRankName() + " has expired for player " + player.getName());
                            // Remove expired rank and use default
                            plugin.getStorageManager().removePlayerRank(playerId);
                            assignDefaultRank(player);
                        } else {
                            playerRanks.put(playerId, rankData);
                            plugin.debug("Loaded rank " + rankData.getRankName() + " for player " + player.getName());
                        }
                    } else {
                        // Player has no rank or invalid rank, use default
                        assignDefaultRank(player);
                    }

                    // Apply permissions and nametag
                    applyPlayerRank(player);
                } finally {
                    lock.unlock();
                }
            });
        }).exceptionally(throwable -> {
            plugin.error("Failed to load rank for player " + player.getName() + ": " + throwable.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> assignDefaultRank(player));
            return null;
        });
    }

    /**
     * Assign default rank to a player
     */
    private void assignDefaultRank(Player player) {
        if (defaultRank != null) {
            PlayerRankData defaultData = new PlayerRankData(
                    player.getUniqueId(),
                    player.getName(),
                    defaultRank.getName(),
                    TimeUtils.getCurrentTimestamp(),
                    null
            );
            playerRanks.put(player.getUniqueId(), defaultData);
            plugin.debug("Assigned default rank to player " + player.getName());
        } else {
            plugin.error("No default rank available for player " + player.getName());
        }
    }

    /**
     * rank setting with validation
     */
    public boolean setPlayerRank(UUID playerId, String playerName, String rankName, String timeStr) {
        if (playerId == null) {
            plugin.error("Cannot set rank for null player ID");
            return false;
        }

        if (!rankExists(rankName)) {
            plugin.error("Attempted to set non-existent rank: " + rankName);
            return false;
        }

        ReentrantLock lock = getPlayerLock(playerId);
        lock.lock();
        try {
            long timeSeconds = timeStr != null ? TimeUtils.parseTime(timeStr) : 0;
            if (timeSeconds < 0) {
                plugin.error("Invalid time format: " + timeStr);
                return false;
            }

            String timeExpires = timeSeconds > 0 ? TimeUtils.getFutureTimestamp(timeSeconds) : null;
            String timeGiven = TimeUtils.getCurrentTimestamp();

            PlayerRankData rankData = new PlayerRankData(playerId, playerName, rankName, timeGiven, timeExpires);
            playerRanks.put(playerId, rankData);

            // Save to storage
            plugin.getStorageManager().setPlayerRank(playerId, playerName, rankName, timeExpires);

            // Apply to online player
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                applyPlayerRank(player);
            }

            // Sync across network if enabled
            if (plugin.getSyncManager() != null) {
                plugin.getSyncManager().syncRankChange(playerId, playerName, rankName, timeExpires);
            }

            plugin.debug("Set rank " + rankName + " for player " + playerName +
                    (timeSeconds > 0 ? " for " + timeStr : " permanently"));
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean removePlayerRank(UUID playerId) {
        if (playerId == null) {
            return false;
        }

        ReentrantLock lock = getPlayerLock(playerId);
        lock.lock();
        try {
            PlayerRankData currentRank = playerRanks.get(playerId);
            if (currentRank == null) {
                return false;
            }

            // Remove from cache
            playerRanks.remove(playerId);

            // Remove from storage
            plugin.getStorageManager().removePlayerRank(playerId);

            // Apply default rank to online player
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                assignDefaultRank(player);
                applyPlayerRank(player);
            }

            // Sync across network if enabled
            if (plugin.getSyncManager() != null) {
                plugin.getSyncManager().syncRankRemoval(playerId, currentRank.getPlayerName());
            }

            plugin.debug("Removed rank from player " + currentRank.getPlayerName());
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Enhanced rank application with nametag and tablist support
     */
    public void applyPlayerRank(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        ReentrantLock lock = getPlayerLock(playerId);
        lock.lock();
        try {
            PlayerRankData playerRankData = playerRanks.get(playerId);
            RankData rankData = null;

            if (playerRankData != null) {
                rankData = getRank(playerRankData.getRankName());

                // Double-check expiration
                if (rankData != null && playerRankData.getTimeExpires() != null &&
                        TimeUtils.hasExpired(playerRankData.getTimeExpires())) {
                    plugin.debug("Rank expired for " + player.getName() + ", removing...");
                    removePlayerRank(playerId);
                    rankData = defaultRank;
                }
            }

            if (rankData == null) {
                rankData = defaultRank;
            }

            if (rankData == null) {
                plugin.error("No rank data available for player " + player.getName());
                return;
            }

            // Apply permissions
            if (!applyPermissions(player, rankData)) {
                plugin.error("Failed to apply permissions for player " + player.getName());
            }

            // Apply nametag if enabled
            if (plugin.getConfig().getBoolean("nametag.enabled", true)) {
                if (!applyNametag(player, rankData)) {
                    plugin.error("Failed to apply nametag for player " + player.getName());
                }
            }

            // Apply tablist if enabled
            if (plugin.getConfig().getBoolean("tablist.enabled", true)) {
                if (!applyTablist(player, rankData)) {
                    plugin.error("Failed to apply tablist for player " + player.getName());
                }
            }

            plugin.debug("Applied rank " + rankData.getName() + " to player " + player.getName());
        } finally {
            lock.unlock();
        }
    }

    /**
     *  permission system with comprehensive error handling
     */
    private boolean applyPermissions(Player player, RankData rankData) {
        UUID playerId = player.getUniqueId();

        try {
            // Remove existing attachment safely
            PermissionAttachment oldAttachment = permissionAttachments.remove(playerId);
            if (oldAttachment != null) {
                try {
                    Set<String> oldPermissions = playerPermissions.get(playerId);
                    if (oldPermissions != null) {
                        plugin.debug("Removing " + oldPermissions.size() + " old permissions from " + player.getName());
                    }
                    player.removeAttachment(oldAttachment);
                } catch (Exception e) {
                    plugin.debug("Error removing old permission attachment: " + e.getMessage());
                }
            }
            playerPermissions.remove(playerId);

            // Create new attachment
            PermissionAttachment attachment = player.addAttachment(plugin);
            Set<String> currentPermissions = new HashSet<>();

            // Apply permissions
            List<String> permissions = rankData.getPermissions();
            int appliedCount = 0;
            int failedCount = 0;

            if (permissions != null && !permissions.isEmpty()) {
                for (String permission : permissions) {
                    if (permission == null || permission.trim().isEmpty()) {
                        continue;
                    }

                    String perm = permission.trim();
                    boolean value = true;

                    // Handle negative permissions
                    if (perm.startsWith("-")) {
                        value = false;
                        perm = perm.substring(1).trim();
                        if (perm.isEmpty()) {
                            failedCount++;
                            continue;
                        }
                    }

                    try {
                        // Register permission if it doesn't exist
                        registerPermissionIfNeeded(perm);

                        // Set permission
                        attachment.setPermission(perm, value);
                        currentPermissions.add(perm + ":" + value);
                        appliedCount++;

                    } catch (Exception e) {
                        plugin.debug("Failed to apply permission " + perm + " to " + player.getName() + ": " + e.getMessage());
                        failedCount++;
                    }
                }
            }

            // Store attachment and permissions
            permissionAttachments.put(playerId, attachment);
            playerPermissions.put(playerId, currentPermissions);

            // Force permission recalculation with retry
            recalculatePermissionsWithRetry(player);

            plugin.debug("Applied " + appliedCount + " permissions to " + player.getName() +
                    (failedCount > 0 ? " (failed: " + failedCount + ")" : ""));

            return failedCount == 0;
        } catch (Exception e) {
            plugin.error("Critical error applying permissions to " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Register permission with bukkit if it doesn't exist
     */
    private void registerPermissionIfNeeded(String perm) {
        if (!perm.equals("*") && Bukkit.getPluginManager().getPermission(perm) == null) {
            try {
                Permission bukkitPerm = new Permission(perm, PermissionDefault.FALSE);
                Bukkit.getPluginManager().addPermission(bukkitPerm);
                plugin.debug("Registered new permission: " + perm);
            } catch (Exception e) {
                // Permission might already exist, ignore
            }
        }
    }

    /**
     * Recalculate permissions with retry mechanism
     */
    private void recalculatePermissionsWithRetry(Player player) {
        try {
            player.recalculatePermissions();

            // Delayed recalculation for better reliability
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.recalculatePermissions();
                }
            }, 2L);
        } catch (Exception e) {
            plugin.debug("Error recalculating permissions: " + e.getMessage());
        }
    }

    /**
     * Enhanced nametag application with automatic gaps
     */
    private boolean applyNametag(Player player, RankData rankData) {
        try {
            Scoreboard scoreboard = player.getScoreboard();
            if (scoreboard == null) {
                scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                if (scoreboard != null) {
                    player.setScoreboard(scoreboard);
                } else {
                    plugin.error("Failed to get scoreboard for nametag application");
                    return false;
                }
            }

            String teamName = TEAM_PREFIX + String.format("%04d", Math.max(0, Math.min(9999, rankData.getWeight())));

            // Clean up old teams first
            cleanupPlayerTeams(scoreboard, player.getName());

            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }

            // Process prefix and suffix with automatic gaps
            String rawPrefix = rankData.getPrefix();
            String rawSuffix = rankData.getSuffix();

            // Add spaces for gaps if prefix/suffix exist and don't already end/start with space
            String prefix = "";
            String suffix = "";

            if (rawPrefix != null && !rawPrefix.isEmpty()) {
                prefix = rawPrefix;
                // Add space after prefix if it doesn't already end with one
                if (!prefix.endsWith(" ")) {
                    prefix += " ";
                }
            }

            if (rawSuffix != null && !rawSuffix.isEmpty()) {
                suffix = rawSuffix;
                // Add space before suffix if it doesn't already start with one
                if (!suffix.startsWith(" ")) {
                    suffix = " " + suffix;
                }
            }

            // Process and truncate prefix/suffix intelligently
            String processedPrefix = processNametagText(prefix, true);
            String processedSuffix = processNametagText(suffix, false);

            team.setPrefix(processedPrefix);
            team.setSuffix(processedSuffix);

            // Add player to team
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }

            plugin.debug("Applied nametag to " + player.getName() + " with team " + teamName +
                    " (prefix: '" + processedPrefix + "', suffix: '" + processedSuffix + "')");

            return true;
        } catch (Exception e) {
            plugin.error("Failed to apply nametag to " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Apply tablist formatting with automatic gaps
     */
    private boolean applyTablist(Player player, RankData rankData) {
        try {
            // Get raw prefix and suffix
            String rawPrefix = rankData.getPrefix();
            String rawSuffix = rankData.getSuffix();

            // Build tablist display name with gaps
            StringBuilder displayName = new StringBuilder();

            // Add prefix with space if it exists
            if (rawPrefix != null && !rawPrefix.isEmpty()) {
                String colorizedPrefix = MessageUtils.colorize(rawPrefix);
                displayName.append(colorizedPrefix);
                // Add space after prefix if it doesn't already end with one
                if (!colorizedPrefix.endsWith(" ")) {
                    displayName.append(" ");
                }
            }

            // Add player name
            displayName.append(player.getName());

            // Add suffix with space if it exists
            if (rawSuffix != null && !rawSuffix.isEmpty()) {
                String colorizedSuffix = MessageUtils.colorize(rawSuffix);
                // Add space before suffix if it doesn't already start with one
                if (!colorizedSuffix.startsWith(" ")) {
                    displayName.append(" ");
                }
                displayName.append(colorizedSuffix);
            }

            // Apply to tablist with length limit (40 characters for tablist)
            String finalDisplayName = displayName.toString();
            if (finalDisplayName.length() > 40) {
                // Intelligently truncate while preserving important parts
                finalDisplayName = truncateTablistName(finalDisplayName, rawPrefix, player.getName(), rawSuffix);
            }

            // Set the player list name
            player.setPlayerListName(finalDisplayName);

            plugin.debug("Applied tablist name to " + player.getName() + ": '" + finalDisplayName + "'");
            return true;

        } catch (Exception e) {
            plugin.error("Failed to apply tablist to " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Intelligently truncate tablist name while preserving important parts
     */
    private String truncateTablistName(String fullName, String rawPrefix, String playerName, String rawSuffix) {
        try {
            // Always prioritize showing the player name
            String colorizedPrefix = rawPrefix != null ? MessageUtils.colorize(rawPrefix) : "";
            String colorizedSuffix = rawSuffix != null ? MessageUtils.colorize(rawSuffix) : "";

            // Calculate available space (40 - player name - 2 spaces for gaps)
            int availableSpace = 40 - playerName.length() - 2;

            if (availableSpace <= 0) {
                // If player name alone is too long, just return the player name
                return playerName.length() > 40 ? playerName.substring(0, 40) : playerName;
            }

            // Try to fit both prefix and suffix
            int prefixLength = MessageUtils.stripColor(colorizedPrefix).length();
            int suffixLength = MessageUtils.stripColor(colorizedSuffix).length();

            if (prefixLength + suffixLength <= availableSpace) {
                // Both fit, return full name
                StringBuilder result = new StringBuilder();
                if (!colorizedPrefix.isEmpty()) {
                    result.append(colorizedPrefix);
                    if (!colorizedPrefix.endsWith(" ")) result.append(" ");
                }
                result.append(playerName);
                if (!colorizedSuffix.isEmpty()) {
                    if (!colorizedSuffix.startsWith(" ")) result.append(" ");
                    result.append(colorizedSuffix);
                }
                return result.toString();
            }

            // Priority: Prefix > Player Name > Suffix (truncate suffix first)
            if (prefixLength <= availableSpace - 2) { // Leave some space for suffix
                int remainingSpace = availableSpace - prefixLength;
                String truncatedSuffix = colorizedSuffix;

                if (suffixLength > remainingSpace) {
                    String strippedSuffix = MessageUtils.stripColor(colorizedSuffix);
                    if (remainingSpace > 2) {
                        truncatedSuffix = MessageUtils.colorize(strippedSuffix.substring(0, remainingSpace - 2) + "..");
                    } else {
                        truncatedSuffix = "";
                    }
                }

                StringBuilder result = new StringBuilder();
                result.append(colorizedPrefix);
                if (!colorizedPrefix.endsWith(" ")) result.append(" ");
                result.append(playerName);
                if (!truncatedSuffix.isEmpty()) {
                    if (!truncatedSuffix.startsWith(" ")) result.append(" ");
                    result.append(truncatedSuffix);
                }
                return result.toString();
            }

            // If prefix is too long, truncate it and skip suffix
            if (availableSpace > 4) {
                String strippedPrefix = MessageUtils.stripColor(colorizedPrefix);
                String truncatedPrefix = MessageUtils.colorize(strippedPrefix.substring(0, availableSpace - 2) + "..");
                return truncatedPrefix + " " + playerName;
            }

            // Last resort: just player name
            return playerName;

        } catch (Exception e) {
            plugin.debug("Error truncating tablist name: " + e.getMessage());
            return playerName; // Fallback to just player name
        }
    }

    /**
     * Intelligent nametag text processing
     */
    private String processNametagText(String text, boolean isPrefix) {
        if (text == null) return "";

        String colored = MessageUtils.colorize(text);
        if (colored.length() <= 16) {
            return colored;
        }

        // Try to preserve important parts
        String stripped = MessageUtils.stripColor(colored);
        if (stripped.length() <= 14) {
            // Try to fit with some colors
            return colored.length() > 16 ? colored.substring(0, 16) : colored;
        }

        // Truncate intelligently
        if (isPrefix) {
            return MessageUtils.colorize(stripped.substring(0, 14) + "..");
        } else {
            return MessageUtils.colorize(".." + stripped.substring(Math.max(0, stripped.length() - 14)));
        }
    }

    /**
     * Clean up old rank teams for a player
     */
    private void cleanupPlayerTeams(Scoreboard scoreboard, String playerName) {
        try {
            for (Team team : new HashSet<>(scoreboard.getTeams())) {
                if (team.getName().startsWith(TEAM_PREFIX) && team.hasEntry(playerName)) {
                    team.removeEntry(playerName);
                }
            }
        } catch (Exception e) {
            plugin.debug("Error cleaning up teams: " + e.getMessage());
        }
    }

    /**
     * Enhanced player cleanup with tablist reset
     */
    public void removePlayer(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        ReentrantLock lock = getPlayerLock(playerId);
        lock.lock();
        try {
            plugin.debug("Removing player data for " + player.getName());

            // Remove from caches
            playerRanks.remove(playerId);
            playerPermissions.remove(playerId);

            // Remove permission attachment
            PermissionAttachment attachment = permissionAttachments.remove(playerId);
            if (attachment != null) {
                try {
                    player.removeAttachment(attachment);
                } catch (Exception e) {
                    plugin.debug("Permission attachment cleanup error: " + e.getMessage());
                }
            }

            // Reset tablist name
            try {
                player.setPlayerListName(player.getName());
            } catch (Exception e) {
                plugin.debug("Tablist cleanup error: " + e.getMessage());
            }

            // Clean up scoreboard teams
            cleanupPlayerScoreboard(player);

            plugin.debug("Cleaned up all data for player " + player.getName());
        } finally {
            lock.unlock();
            // Remove the lock to prevent memory leaks
            playerLocks.remove(playerId);
        }
    }

    /**
     * Clean up player scoreboard data
     */
    private void cleanupPlayerScoreboard(Player player) {
        try {
            Scoreboard scoreboard = player.getScoreboard();
            if (scoreboard != null) {
                cleanupPlayerTeams(scoreboard, player.getName());
            }
        } catch (Exception e) {
            plugin.debug("Scoreboard cleanup error for " + player.getName() + ": " + e.getMessage());
        }
    }

    // Enhanced utility methods
    public boolean playerHasPermission(UUID playerId, String permission) {
        Set<String> permissions = playerPermissions.get(playerId);
        if (permissions == null) return false;

        return permissions.contains(permission + ":true") ||
                permissions.contains("*:true") ||
                (!permissions.contains(permission + ":false") &&
                        permissions.stream().anyMatch(p -> p.startsWith("*:true")));
    }

    public Set<String> getPlayerPermissions(UUID playerId) {
        return new HashSet<>(playerPermissions.getOrDefault(playerId, new HashSet<>()));
    }

    // Existing utility methods with null safety
    public String getPlayerRankName(UUID playerId) {
        if (playerId == null) return "Unknown";
        PlayerRankData data = playerRanks.get(playerId);
        return data != null ? data.getRankName() : (defaultRank != null ? defaultRank.getName() : "Unknown");
    }

    public String getPlayerPrefix(UUID playerId) {
        if (playerId == null) return "";
        PlayerRankData playerData = playerRanks.get(playerId);
        if (playerData != null) {
            RankData rankData = getRank(playerData.getRankName());
            if (rankData != null) {
                return rankData.getPrefix();
            }
        }
        return defaultRank != null ? defaultRank.getPrefix() : "";
    }

    public String getPlayerSuffix(UUID playerId) {
        if (playerId == null) return "";
        PlayerRankData playerData = playerRanks.get(playerId);
        if (playerData != null) {
            RankData rankData = getRank(playerData.getRankName());
            if (rankData != null) {
                return rankData.getSuffix();
            }
        }
        return defaultRank != null ? defaultRank.getSuffix() : "";
    }

    public int getPlayerWeight(UUID playerId) {
        if (playerId == null) return 1;
        PlayerRankData playerData = playerRanks.get(playerId);
        if (playerData != null) {
            RankData rankData = getRank(playerData.getRankName());
            if (rankData != null) {
                return rankData.getWeight();
            }
        }
        return defaultRank != null ? defaultRank.getWeight() : 1;
    }

    public boolean hasRank(UUID playerId, String rankName) {
        if (playerId == null || rankName == null) return false;
        PlayerRankData data = playerRanks.get(playerId);
        return data != null && data.getRankName().equalsIgnoreCase(rankName);
    }

    public List<String> getAllPlayerRanks(UUID playerId) {
        if (playerId == null) return new ArrayList<>();
        PlayerRankData data = playerRanks.get(playerId);
        if (data != null) {
            return Arrays.asList(data.getRankName());
        }
        return defaultRank != null ? Arrays.asList(defaultRank.getName()) : new ArrayList<>();
    }

    /**
     * Get rank statistics
     */
    public Map<String, Integer> getRankStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        for (PlayerRankData data : playerRanks.values()) {
            stats.merge(data.getRankName(), 1, Integer::sum);
        }
        return stats;
    }

    /**
     * Check if any players need rank expiration processing
     */
    public void processRankExpirations() {
        List<UUID> expiredPlayers = new ArrayList<>();
        String currentTime = TimeUtils.getCurrentTimestamp();

        for (Map.Entry<UUID, PlayerRankData> entry : playerRanks.entrySet()) {
            PlayerRankData data = entry.getValue();
            if (data.getTimeExpires() != null && TimeUtils.hasExpired(data.getTimeExpires())) {
                expiredPlayers.add(entry.getKey());
            }
        }

        for (UUID playerId : expiredPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                plugin.debug("Processing rank expiration for " + player.getName());
                removePlayerRank(playerId);
            }
        }
    }

    /**
     * Validate configuration integrity
     */
    public boolean validateConfiguration() {
        if (ranks.isEmpty()) {
            plugin.error("No ranks configured!");
            return false;
        }

        if (defaultRank == null) {
            plugin.error("No default rank configured!");
            return false;
        }

        // Check for weight conflicts
        Map<Integer, List<String>> weightGroups = new HashMap<>();
        for (RankData rank : ranks.values()) {
            weightGroups.computeIfAbsent(rank.getWeight(), k -> new ArrayList<>()).add(rank.getName());
        }

        for (Map.Entry<Integer, List<String>> entry : weightGroups.entrySet()) {
            if (entry.getValue().size() > 1) {
                plugin.log("Warning: Multiple ranks with same weight " + entry.getKey() + ": " +
                        String.join(", ", entry.getValue()));
            }
        }

        return true;
    }
}