package dev.abdelrahman.rankcorex.commands.subcommands;

import dev.abdelrahman.rankcorex.Rankcorex;
import dev.abdelrahman.rankcorex.models.PlayerRankData;
import dev.abdelrahman.rankcorex.models.RankData;
import dev.abdelrahman.rankcorex.utils.MessageUtils;
import dev.abdelrahman.rankcorex.utils.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RankSetCommand {

    private final Rankcorex plugin;

    // Time suggestions for tab completion
    private static final List<String> TIME_SUGGESTIONS = Arrays.asList(
            "30s", "5m", "30m", "1h", "6h", "12h", "1d", "3d", "7d", "14d", "30d", "90d", "1y", "permanent"
    );

    // Maximum player search results for tab completion
    private static final int MAX_PLAYER_SUGGESTIONS = 20;

    public RankSetCommand(Rankcorex plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Permission check
        if (!sender.hasPermission("rankcorex.set")) {
            sender.sendMessage(MessageUtils.colorize(MessageUtils.NO_PERMISSION));
            return true;
        }

        // Argument validation
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.colorize(MessageUtils.USAGE_SET));
            sendDetailedUsage(sender);
            return true;
        }

        if (args.length > 3) {
            sender.sendMessage(MessageUtils.colorize("&cToo many arguments! " + MessageUtils.USAGE_SET));
            return true;
        }

        String playerName = args[0].trim();
        String rankName = args[1].trim();
        String timeStr = args.length > 2 ? args[2].trim() : null;

        // Validate inputs
        if (playerName.isEmpty()) {
            sender.sendMessage(MessageUtils.colorize("&cPlayer name cannot be empty!"));
            return true;
        }

        if (rankName.isEmpty()) {
            sender.sendMessage(MessageUtils.colorize("&cRank name cannot be empty!"));
            return true;
        }

        // Check if rank exists (case-insensitive)
        RankData targetRank = plugin.getRankManager().getRank(rankName);
        if (targetRank == null) {
            sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_NOT_EXISTS, "rank", rankName));

            // Suggest similar rank names
            suggestSimilarRanks(sender, rankName);
            return true;
        }

        // Use the actual rank name (correct case)
        rankName = targetRank.getName();

        // Validate time format if provided
        long timeSeconds = 0;
        if (timeStr != null && !timeStr.isEmpty() && !timeStr.equalsIgnoreCase("permanent")) {
            timeSeconds = TimeUtils.parseTime(timeStr);
            if (timeSeconds <= 0) {
                sender.sendMessage(MessageUtils.colorize(MessageUtils.INVALID_TIME_FORMAT));
                sender.sendMessage(MessageUtils.colorize("&7Examples: &f30s&7, &f5m&7, &f1h&7, &f7d&7, &f30d&7, &f1y"));
                return true;
            }

            // Validate time bounds
            if (!validateTimeRange(sender, timeSeconds)) {
                return true;
            }
        }

        // Get and validate player
        OfflinePlayer target = getTargetPlayer(playerName);
        if (target == null) {
            sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.PLAYER_NOT_FOUND, "player", playerName));
            return true;
        }

        // Check if player already has the same rank
        if (hasPlayerSameRank(target.getUniqueId(), rankName)) {
            sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.SAME_RANK, "rank", rankName));
            return true;
        }

        // Permission check for rank hierarchy (if sender is a player)
        if (sender instanceof Player && !canSetRank(sender, targetRank)) {
            sender.sendMessage(MessageUtils.colorize("&cYou cannot set a rank higher than or equal to your own!"));
            return true;
        }

        // Execute the rank setting asynchronously
        executeRankSet(sender, target, rankName, timeStr, timeSeconds);

        return true;
    }

    /**
     * Execute rank setting with proper async handling
     */
    private void executeRankSet(CommandSender sender, OfflinePlayer target, String rankName, String timeStr, long timeSeconds) {
        CompletableFuture.runAsync(() -> {
            try {
                // Set the rank
                boolean success = plugin.getRankManager().setPlayerRank(
                        target.getUniqueId(),
                        target.getName(),
                        rankName,
                        timeStr
                );

                // Send response on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        sendSuccessMessage(sender, target, rankName, timeStr);

                        // Notify target player if online
                        notifyTargetPlayer(target, rankName, timeStr, sender.getName());

                        // Log the action
                        plugin.log(sender.getName() + " set " + target.getName() + "'s rank to " + rankName +
                                (timeStr != null && !timeStr.equalsIgnoreCase("permanent") ? " for " + timeStr : " permanently"));
                    } else {
                        sender.sendMessage(MessageUtils.colorize("&cFailed to set rank! Check console for details."));
                    }
                });
            } catch (Exception e) {
                plugin.error("Error setting rank for " + target.getName() + ": " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(MessageUtils.colorize(MessageUtils.STORAGE_ERROR));
                });
            }
        });
    }

    /**
     * Send detailed usage information
     */
    private void sendDetailedUsage(CommandSender sender) {
        sender.sendMessage(MessageUtils.colorize("&7Examples:"));
        sender.sendMessage(MessageUtils.colorize("&f  /rank set Player123 VIP &7- Set permanent VIP rank"));
        sender.sendMessage(MessageUtils.colorize("&f  /rank set Player123 VIP 7d &7- Set VIP rank for 7 days"));
        sender.sendMessage(MessageUtils.colorize("&f  /rank set Player123 VIP permanent &7- Set permanent VIP rank"));
    }

    /**
     * Validate time range limits
     */
    private boolean validateTimeRange(CommandSender sender, long timeSeconds) {
        // Check minimum time (30 seconds)
        if (timeSeconds < 30) {
            sender.sendMessage(MessageUtils.colorize("&cTime must be at least 30 seconds!"));
            return false;
        }

        // Check maximum time (10 years)
        long maxTime = 10L * 365 * 24 * 60 * 60; // 10 years in seconds
        if (timeSeconds > maxTime) {
            sender.sendMessage(MessageUtils.colorize("&cTime cannot exceed 10 years!"));
            return false;
        }

        return true;
    }

    /**
     * Get target player with  validation
     */
    private OfflinePlayer getTargetPlayer(String playerName) {
        // Try online players first for exact match
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }

        // Try offline players
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);

        // Validate offline player
        if (offlinePlayer != null && (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline())) {
            return offlinePlayer;
        }

        return null;
    }

    /**
     * Check if player already has the same rank
     */
    private boolean hasPlayerSameRank(UUID playerId, String rankName) {
        PlayerRankData currentRank = plugin.getRankManager().getPlayerRank(playerId);
        if (currentRank == null) {
            // Check if trying to set default rank
            RankData defaultRank = plugin.getRankManager().getDefaultRank();
            return defaultRank != null && defaultRank.getName().equalsIgnoreCase(rankName);
        }
        return currentRank.getRankName().equalsIgnoreCase(rankName);
    }

    /**
     * Check if sender can set the target rank (hierarchy permission)
     */
    private boolean canSetRank(CommandSender sender, RankData targetRank) {
        if (!(sender instanceof Player)) {
            return true; // Console can set any rank
        }

        Player senderPlayer = (Player) sender;

        // Bypass permission check
        if (senderPlayer.hasPermission("rankcorex.set.bypass")) {
            return true;
        }

        // Get sender's rank weight
        int senderWeight = plugin.getRankManager().getPlayerWeight(senderPlayer.getUniqueId());
        int targetWeight = targetRank.getWeight();

        // Can only set ranks with lower weight (lower priority)
        return senderWeight > targetWeight;
    }

    /**
     * Send success message to sender
     */
    private void sendSuccessMessage(CommandSender sender, OfflinePlayer target, String rankName, String timeStr) {
        if (timeStr == null || timeStr.isEmpty() || timeStr.equalsIgnoreCase("permanent")) {
            sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_SET_PERMANENT,
                    "player", target.getName(), "rank", rankName));
        } else {
            sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_SET_TEMPORARY,
                    "player", target.getName(), "rank", rankName, "time", timeStr));
        }
    }

    /**
     * Notify target player about rank change
     */
    private void notifyTargetPlayer(OfflinePlayer target, String rankName, String timeStr, String senderName) {
        if (target.isOnline()) {
            Player onlineTarget = (Player) target;

            if (timeStr == null || timeStr.isEmpty() || timeStr.equalsIgnoreCase("permanent")) {
                onlineTarget.sendMessage(MessageUtils.replacePlaceholders(
                        "&aYour rank has been set to &f{rank} &apermanently by &f{sender}&a!",
                        "rank", rankName, "sender", senderName));
            } else {
                onlineTarget.sendMessage(MessageUtils.replacePlaceholders(
                        "&aYour rank has been set to &f{rank} &afor &f{time} &aby &f{sender}&a!",
                        "rank", rankName, "time", timeStr, "sender", senderName));
            }
        }
    }

    /**
     * Suggest similar rank names when rank not found
     */
    private void suggestSimilarRanks(CommandSender sender, String inputRank) {
        List<String> suggestions = new ArrayList<>();
        String lowerInput = inputRank.toLowerCase();

        for (RankData rank : plugin.getRankManager().getAllRanks()) {
            String rankName = rank.getName();
            String lowerRank = rankName.toLowerCase();

            // Exact contains match or starts with
            if (lowerRank.contains(lowerInput) || lowerInput.contains(lowerRank)) {
                suggestions.add(rankName);
            }
        }

        if (!suggestions.isEmpty()) {
            sender.sendMessage(MessageUtils.colorize("&7Did you mean: &f" + String.join("&7, &f", suggestions)));
        } else {
            sender.sendMessage(MessageUtils.colorize("&7Use &f/rank list &7to see available ranks."));
        }
    }

    /**
     * Enhanced tab completion with better performance and suggestions
     */
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        switch (args.length) {
            case 1:
                // Player names with smart filtering
                completions.addAll(getPlayerCompletions(args[0]));
                break;

            case 2:
                // Rank names with permission filtering
                completions.addAll(getRankCompletions(sender, args[1]));
                break;

            case 3:
                // Time suggestions
                completions.addAll(getTimeCompletions(args[2]));
                break;
        }

        return completions;
    }

    /**
     * Get player name completions
     */
    private List<String> getPlayerCompletions(String partial) {
        String lowerPartial = partial.toLowerCase();
        List<String> completions = new ArrayList<>();

        // Add online players first (prioritized)
        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name != null && name.toLowerCase().startsWith(lowerPartial))
                .limit(10)
                .forEach(completions::add);

        // Add offline players if we need more suggestions
        if (completions.size() < MAX_PLAYER_SUGGESTIONS && partial.length() >= 2) {
            Arrays.stream(Bukkit.getOfflinePlayers())
                    .map(OfflinePlayer::getName)
                    .filter(name -> name != null && name.toLowerCase().startsWith(lowerPartial))
                    .filter(name -> !completions.contains(name)) // Avoid duplicates
                    .limit(MAX_PLAYER_SUGGESTIONS - completions.size())
                    .forEach(completions::add);
        }

        return completions;
    }

    /**
     * Get rank name completions with permission filtering
     */
    private List<String> getRankCompletions(CommandSender sender, String partial) {
        String lowerPartial = partial.toLowerCase();

        return plugin.getRankManager().getAllRanks().stream()
                .filter(rank -> rank.getName().toLowerCase().startsWith(lowerPartial))
                .filter(rank -> canSuggestRank(sender, rank))
                .map(RankData::getName)
                .collect(Collectors.toList());
    }

    /**
     * Check if rank can be suggested to sender (hierarchy check)
     */
    private boolean canSuggestRank(CommandSender sender, RankData rank) {
        if (!(sender instanceof Player)) {
            return true; // Console can see all ranks
        }

        Player senderPlayer = (Player) sender;

        // Bypass permission
        if (senderPlayer.hasPermission("rankcorex.set.bypass")) {
            return true;
        }

        // Check hierarchy permission
        if (!senderPlayer.hasPermission("rankcorex.set.hierarchy")) {
            return true; // No hierarchy restriction
        }

        return canSetRank(sender, rank);
    }

    /**
     * Get time completions
     */
    private List<String> getTimeCompletions(String partial) {
        String lowerPartial = partial.toLowerCase();

        return TIME_SUGGESTIONS.stream()
                .filter(time -> time.startsWith(lowerPartial))
                .collect(Collectors.toList());
    }
}