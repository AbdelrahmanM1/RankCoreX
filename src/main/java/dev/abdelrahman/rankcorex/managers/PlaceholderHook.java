package dev.abdelrahman.rankcorex.managers;

import dev.abdelrahman.rankcorex.Rankcorex;
import dev.abdelrahman.rankcorex.models.PlayerRankData;
import dev.abdelrahman.rankcorex.utils.MessageUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.List;

public class PlaceholderHook extends PlaceholderExpansion {

    private final Rankcorex plugin;

    public PlaceholderHook(Rankcorex plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "rankcorex";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Keep expansion loaded when plugin reloads
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }

        RankManager rankManager = plugin.getRankManager();
        PlayerRankData playerData = rankManager.getPlayerRank(player.getUniqueId());

        switch (params.toLowerCase()) {
            // Basic placeholders
            case "rank":
                return rankManager.getPlayerRankName(player.getUniqueId());

            case "prefix":
                return MessageUtils.colorize(rankManager.getPlayerPrefix(player.getUniqueId()));

            case "suffix":
                return MessageUtils.colorize(rankManager.getPlayerSuffix(player.getUniqueId()));

            case "weight":
                return String.valueOf(rankManager.getPlayerWeight(player.getUniqueId()));

            // Time-based placeholders
            case "expiry":
                if (playerData != null) {
                    return playerData.getTimeRemaining();
                }
                return "Permanent";

            case "time_given":
                if (playerData != null && playerData.getTimeGiven() != null) {
                    return playerData.getTimeGiven();
                }
                return "Unknown";

            case "time_since_given":
                if (playerData != null) {
                    return playerData.getTimeSinceGiven();
                }
                return "Unknown";

            // List placeholders
            case "all_ranks":
                List<String> playerRanks = rankManager.getAllPlayerRanks(player.getUniqueId());
                return String.join(", ", playerRanks);

            // Display placeholders
            case "nametag":
                String prefix = MessageUtils.colorize(rankManager.getPlayerPrefix(player.getUniqueId()));
                String suffix = MessageUtils.colorize(rankManager.getPlayerSuffix(player.getUniqueId()));
                return prefix + player.getName() + suffix;

            case "tabname":
                String tabPrefix = MessageUtils.colorize(rankManager.getPlayerPrefix(player.getUniqueId()));
                return tabPrefix + player.getName();

            default:
                // Check for has_rank_<rank> format
                if (params.startsWith("has_rank_")) {
                    String rankName = params.substring(9); // Remove "has_rank_" prefix
                    return String.valueOf(rankManager.hasRank(player.getUniqueId(), rankName));
                }

                return null; // Placeholder not found
        }
    }
}