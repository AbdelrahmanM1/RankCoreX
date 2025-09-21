package dev.abdelrahman.rankcorex.placeholder;

import dev.abdelrahman.rankcorex.Rankcorex;
import dev.abdelrahman.rankcorex.managers.RankManager;
import dev.abdelrahman.rankcorex.models.PlayerRankData;
import dev.abdelrahman.rankcorex.utils.MessageUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RankExpansion extends PlaceholderExpansion {

    private final Rankcorex plugin;

    public RankExpansion(Rankcorex plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rankcorex"; // %rankcorex_<placeholder>%
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // keep loaded after reload
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            plugin.debug("PlaceholderAPI request with null player for param: " + params);
            return "";
        }

        plugin.debug("PlaceholderAPI request: %rankcorex_" + params + "% for player " + player.getName());

        RankManager rankManager = plugin.getRankManager();
        if (rankManager == null) {
            plugin.debug("RankManager is null!");
            return "";
        }

        PlayerRankData playerData = rankManager.getPlayerRank(player.getUniqueId());

        switch (params.toLowerCase()) {
            case "rank":
                String rankName = rankManager.getPlayerRankName(player.getUniqueId());
                plugin.debug("Returning rank: " + rankName);
                return rankName;

            case "prefix":
                String prefix = MessageUtils.colorize(rankManager.getPlayerPrefix(player.getUniqueId()));
                plugin.debug("Returning prefix: " + prefix);
                return prefix;

            case "suffix":
                String suffix = MessageUtils.colorize(rankManager.getPlayerSuffix(player.getUniqueId()));
                plugin.debug("Returning suffix: " + suffix);
                return suffix;

            case "weight":
                String weight = String.valueOf(rankManager.getPlayerWeight(player.getUniqueId()));
                plugin.debug("Returning weight: " + weight);
                return weight;

            case "expiry":
                String expiry = playerData != null ? playerData.getTimeRemaining() : "Permanent";
                plugin.debug("Returning expiry: " + expiry);
                return expiry;

            case "time_given":
                String timeGiven = (playerData != null && playerData.getTimeGiven() != null)
                        ? playerData.getTimeGiven()
                        : "Unknown";
                plugin.debug("Returning time_given: " + timeGiven);
                return timeGiven;

            case "time_since_given":
                String timeSince = playerData != null ? playerData.getTimeSinceGiven() : "Unknown";
                plugin.debug("Returning time_since_given: " + timeSince);
                return timeSince;

            case "all_ranks":
                List<String> playerRanks = rankManager.getAllPlayerRanks(player.getUniqueId());
                String allRanks = String.join(", ", playerRanks);
                plugin.debug("Returning all_ranks: " + allRanks);
                return allRanks;

            case "nametag":
                String nametagPrefix = MessageUtils.colorize(rankManager.getPlayerPrefix(player.getUniqueId()));
                String nametagSuffix = MessageUtils.colorize(rankManager.getPlayerSuffix(player.getUniqueId()));
                String nametag = nametagPrefix + player.getName() + nametagSuffix;
                plugin.debug("Returning nametag: " + nametag);
                return nametag;

            case "tabname":
                String tabPrefix = MessageUtils.colorize(rankManager.getPlayerPrefix(player.getUniqueId()));
                String tabname = tabPrefix + player.getName();
                plugin.debug("Returning tabname: " + tabname);
                return tabname;

            default:
                // Custom check: %rankcorex_has_rank_Admin%
                if (params.startsWith("has_rank_")) {
                    String rankToCheck = params.substring(9);
                    boolean hasRank = rankManager.hasRank(player.getUniqueId(), rankToCheck);
                    plugin.debug("Returning has_rank_" + rankToCheck + ": " + hasRank);
                    return String.valueOf(hasRank);
                }

                plugin.debug("Unknown placeholder: " + params);
                return null;
        }
    }
}