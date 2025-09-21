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

    // Version detection for universal compatibility
    private String mcVersion;
    private boolean isOldMinecraft;
    private boolean isVeryOldMinecraft;

    // Constants for validation
    private static final String PERMISSION_PATTERN = "^[a-zA-Z0-9._*-]+$";
    private static final int MAX_PREFIX_LENGTH = 16;
    private static final int MAX_SUFFIX_LENGTH = 16;
    private static final String TEAM_PREFIX = "rc_";

    public RankManager(Rankcorex plugin) {
        this.plugin = plugin;

        // Detect Minecraft version for compatibility
        this.mcVersion = Bukkit.getBukkitVersion();
        this.isOldMinecraft = mcVersion.contains("1.8") || mcVersion.contains("1.9") ||
                mcVersion.contains("1.10") || mcVersion.contains("1.11") || mcVersion.contains("1.12");
        this.isVeryOldMinecraft = mcVersion.contains("1.8") || mcVersion.contains("1.7");

        plugin.log("Running on Minecraft " + mcVersion +
                (isOldMinecraft ? " (OLD VERSION)" : " (NEW VERSION)") +
                (isVeryOldMinecraft ? " - Using compatibility mode" : ""));
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
     * player rank loading with better error handling
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
     *  rank application with nametag and tablist support
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

            // Apply permissions with universal compatibility
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
     * UNIVERSAL PERMISSION SYSTEM - Works with all Minecraft versions
     */
    private boolean applyPermissions(Player player, RankData rankData) {
        UUID playerId = player.getUniqueId();
        plugin.debug("Applying permissions to " + player.getName() + " (MC: " + mcVersion + ")");

        try {
            // Step 1: Clean up old permissions
            cleanupOldPermissions(player, playerId);

            // Step 2: Verify player is still online
            if (!player.isOnline()) {
                plugin.debug("Player " + player.getName() + " went offline, skipping permissions");
                return false;
            }

            // Step 3: Create new permission attachment
            PermissionAttachment attachment = createPermissionAttachment(player);
            if (attachment == null) {
                plugin.error("Failed to create permission attachment for " + player.getName());
                return false;
            }

            // Step 4: Apply all permissions from the rank
            Set<String> appliedPermissions = new HashSet<>();
            List<String> rankPermissions = rankData.getPermissions();

            if (rankPermissions != null && !rankPermissions.isEmpty()) {
                for (String permission : rankPermissions) {
                    if (addSinglePermission(attachment, permission, player.getName())) {
                        appliedPermissions.add(permission);
                    }
                }
            }

            // Step 5: Store attachment and permissions
            permissionAttachments.put(playerId, attachment);
            playerPermissions.put(playerId, appliedPermissions);

            // Step 6: Update player permissions with version-specific timing
            updatePlayerPermissions(player, rankPermissions);

            plugin.debug("Successfully applied " + appliedPermissions.size() + " permissions to " + player.getName());
            return true;

        } catch (Exception e) {
            plugin.error("Critical error applying permissions to " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Clean up old permissions safely
     */
    private void cleanupOldPermissions(Player player, UUID playerId) {
        PermissionAttachment oldAttachment = permissionAttachments.remove(playerId);
        if (oldAttachment == null) {
            return;
        }

        try {
            if (isVeryOldMinecraft) {
                // For very old versions, try different removal methods
                try {
                    oldAttachment.remove();
                    plugin.debug("Removed old attachment using remove() for " + player.getName());
                } catch (NoSuchMethodError e) {
                    player.removeAttachment(oldAttachment);
                    plugin.debug("Removed old attachment using removeAttachment() for " + player.getName());
                }
            } else {
                // For newer versions
                oldAttachment.remove();
            }
        } catch (Exception e) {
            plugin.debug("Error removing old attachment: " + e.getMessage());
            // Fallback method
            try {
                player.removeAttachment(oldAttachment);
            } catch (Exception e2) {
                plugin.debug("Fallback attachment removal failed: " + e2.getMessage());
            }
        }

        playerPermissions.remove(playerId);
    }

    /**
     * Create permission attachment with safety checks
     */
    private PermissionAttachment createPermissionAttachment(Player player) {
        try {
            if (!plugin.isEnabled()) {
                plugin.error("Plugin is not enabled, cannot create permission attachment");
                return null;
            }

            if (!player.isOnline()) {
                plugin.debug("Player " + player.getName() + " is not online, skipping attachment creation");
                return null;
            }

            PermissionAttachment attachment = player.addAttachment(plugin);

            if (attachment == null) {
                plugin.error("Permission attachment creation returned null for " + player.getName());
                return null;
            }

            plugin.debug("Successfully created permission attachment for " + player.getName());
            return attachment;

        } catch (Exception e) {
            plugin.error("Exception creating permission attachment for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Add a single permission safely
     */
    private boolean addSinglePermission(PermissionAttachment attachment, String permission, String playerName) {
        if (permission == null || permission.trim().isEmpty()) {
            return false;
        }

        String cleanPerm = permission.trim();
        boolean isPositive = true;

        // Handle negative permissions
        if (cleanPerm.startsWith("-")) {
            isPositive = false;
            cleanPerm = cleanPerm.substring(1).trim();
            if (cleanPerm.isEmpty()) {
                return false;
            }
        }

        // Validate permission format
        if (!isValidPermissionString(cleanPerm)) {
            plugin.debug("Invalid permission string: " + cleanPerm);
            return false;
        }

        try {
            attachment.setPermission(cleanPerm, isPositive);
            plugin.debug("Added permission " + cleanPerm + " = " + isPositive + " to " + playerName);
            return true;
        } catch (Exception e) {
            plugin.debug("Failed to add permission " + cleanPerm + " to " + playerName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Validate permission string format
     */
    private boolean isValidPermissionString(String permission) {
        if (permission == null || permission.isEmpty()) {
            return false;
        }

        if (permission.contains("..") || permission.startsWith(".") || permission.endsWith(".")) {
            return false;
        }

        if (permission.length() > 255) {
            return false;
        }

        return true;
    }

    /**
     * Update player permissions with version-specific timing
     */
    private void updatePlayerPermissions(Player player, List<String> expectedPermissions) {
        try {
            // Immediate recalculation
            player.recalculatePermissions();

            // Delayed recalculation with version-specific timing
            int delayTicks = isVeryOldMinecraft ? 15 : (isOldMinecraft ? 10 : 5);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    try {
                        player.recalculatePermissions();
                        plugin.debug("Updated permissions for " + player.getName() + " (delayed)");

                        // Additional validation for old versions
                        if (isVeryOldMinecraft) {
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (player.isOnline()) {
                                    player.recalculatePermissions();
                                    validatePermissionsWorking(player, expectedPermissions);
                                    plugin.debug("Final permission update for " + player.getName());
                                }
                            }, 10L);
                        }

                    } catch (Exception e) {
                        plugin.debug("Error in delayed permission update: " + e.getMessage());
                    }
                }
            }, delayTicks);

        } catch (Exception e) {
            plugin.debug("Error updating permissions: " + e.getMessage());
        }
    }

    /**
     * Validate that permissions are actually working
     */
    private void validatePermissionsWorking(Player player, List<String> expectedPermissions) {
        if (expectedPermissions == null || expectedPermissions.isEmpty()) {
            return;
        }

        boolean foundWorkingPermission = false;

        for (String perm : expectedPermissions) {
            if (perm == null || perm.trim().isEmpty() || perm.startsWith("-")) {
                continue;
            }

            String cleanPerm = perm.trim();
            try {
                boolean hasPermission = player.hasPermission(cleanPerm);
                plugin.debug("Permission validation - " + player.getName() + " has " + cleanPerm + ": " + hasPermission);

                if (hasPermission) {
                    foundWorkingPermission = true;
                    break;
                }
            } catch (Exception e) {
                plugin.debug("Error testing permission " + cleanPerm + ": " + e.getMessage());
            }
        }

        if (!foundWorkingPermission && !expectedPermissions.isEmpty()) {
            plugin.debug("Permission validation failed for " + player.getName() + ", attempting recovery...");
            // Attempt to reapply permissions
            UUID playerId = player.getUniqueId();
            PlayerRankData playerData = playerRanks.get(playerId);
            if (playerData != null) {
                RankData rankData = getRank(playerData.getRankName());
                if (rankData != null) {
                    plugin.debug("Retrying permission application for " + player.getName());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            applyPermissions(player, rankData);
                        }
                    }, 5L);
                }
            }
        }
    }

    /**
     *  nametag application with automatic gaps
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
     * UPDATED: Enhanced player cleanup with universal version support
     */
    public void removePlayer(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        ReentrantLock lock = getPlayerLock(playerId);
        lock.lock();
        try {
            plugin.debug("Cleaning up " + player.getName());

            // Remove from our storage
            playerRanks.remove(playerId);
            playerPermissions.remove(playerId);

            // Remove permission attachment with universal compatibility
            PermissionAttachment attachment = permissionAttachments.remove(playerId);
            if (attachment != null) {
                try {
                    if (isVeryOldMinecraft) {
                        // For very old versions, try different removal methods
                        try {
                            attachment.remove();
                            plugin.debug("Removed attachment using remove() for " + player.getName());
                        } catch (NoSuchMethodError e) {
                            player.removeAttachment(attachment);
                            plugin.debug("Removed attachment using removeAttachment() for " + player.getName());
                        }
                    } else {
                        // For newer versions
                        attachment.remove();
                    }
                } catch (Exception e) {
                    plugin.debug("Permission attachment cleanup error: " + e.getMessage());
                    // Fallback method
                    try {
                        player.removeAttachment(attachment);
                    } catch (Exception e2) {
                        plugin.debug("Fallback attachment removal failed: " + e2.getMessage());
                    }
                }
            }

            // Reset tablist name
            try {
                player.setPlayerListName(null); // Reset to default
            } catch (Exception e) {
                plugin.debug("Tablist cleanup error: " + e.getMessage());
            }

            // Clean up scoreboard teams
            cleanupPlayerScoreboard(player);

            plugin.debug("Finished cleaning up " + player.getName());

        } finally {
            lock.unlock();
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

    /**
     * NEW: Debug method to help diagnose permission issues
     */
    public void debugPlayerPermissions(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        PlayerRankData playerData = playerRanks.get(playerId);

        plugin.log("=== Permission Debug for " + player.getName() + " ===");
        plugin.log("Server Version: " + mcVersion +
                (isOldMinecraft ? " (OLD)" : " (NEW)") +
                (isVeryOldMinecraft ? " (VERY OLD)" : ""));

        if (playerData == null) {
            plugin.log("No rank data found!");
            return;
        }

        RankData rank = getRank(playerData.getRankName());
        if (rank == null) {
            plugin.log("Invalid rank: " + playerData.getRankName());
            return;
        }

        plugin.log("Rank: " + rank.getName());
        plugin.log("Has attachment: " + (permissionAttachments.get(playerId) != null));

        List<String> permissions = rank.getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            plugin.log("No permissions configured for this rank!");
            return;
        }

        plugin.log("Testing " + permissions.size() + " permissions:");
        for (String perm : permissions) {
            if (perm != null && !perm.trim().isEmpty() && !perm.startsWith("-")) {
                boolean hasIt = player.hasPermission(perm.trim());
                plugin.log("  " + perm + " = " + hasIt + (hasIt ? " ✓" : " ✗"));
            }
        }

        // Test wildcard
        plugin.log("Wildcard (*): " + player.hasPermission("*"));

        // Show effective permissions count
        try {
            int effectiveCount = player.getEffectivePermissions().size();
            plugin.log("Total effective permissions: " + effectiveCount);
        } catch (Exception e) {
            plugin.log("Could not get effective permissions: " + e.getMessage());
        }
    }

    /**
     * NEW: Force refresh permissions for a player
     */
    public void refreshPlayerPermissions(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        plugin.log("Force refreshing permissions for " + player.getName());

        UUID playerId = player.getUniqueId();
        PlayerRankData playerData = playerRanks.get(playerId);

        if (playerData != null) {
            RankData rank = getRank(playerData.getRankName());
            if (rank != null) {
                // Force reapply permissions
                applyPermissions(player, rank);
            }
        }
    }

    /**
     * NEW: Get version info for debugging
     */
    public String getVersionInfo() {
        return "MC Version: " + mcVersion +
                " | Old: " + isOldMinecraft +
                " | Very Old: " + isVeryOldMinecraft +
                " | Players: " + playerRanks.size() +
                " | Attachments: " + permissionAttachments.size();
    }
}
