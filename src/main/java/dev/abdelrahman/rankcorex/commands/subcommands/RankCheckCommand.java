package dev.abdelrahman.rankcorex.commands.subcommands;

import dev.abdelrahman.rankcorex.Rankcorex;
import dev.abdelrahman.rankcorex.models.PlayerRankData;
import dev.abdelrahman.rankcorex.models.RankData;
import dev.abdelrahman.rankcorex.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class RankCheckCommand {

    private final Rankcorex plugin;

    public RankCheckCommand(Rankcorex plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rankcorex.check")) {
            sender.sendMessage(MessageUtils.colorize(MessageUtils.NO_PERMISSION));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(MessageUtils.colorize(MessageUtils.USAGE_CHECK));
            return true;
        }

        String playerName = args[0];

        // Get player (offline or online)
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.PLAYER_NOT_FOUND, "player", playerName));
            return true;
        }

        // Get player's rank data
        PlayerRankData playerRankData = plugin.getRankManager().getPlayerRank(target.getUniqueId());
        String rankName = plugin.getRankManager().getPlayerRankName(target.getUniqueId());
        RankData rankData = plugin.getRankManager().getRank(rankName);

        if (rankData == null) {
            sender.sendMessage(MessageUtils.colorize("&cNo rank data found for player " + target.getName()));
            return true;
        }

        // Display rank information
        sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_INFO_HEADER, "player", target.getName()));
        sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_INFO_CURRENT, "rank", rankData.getName()));
        sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_INFO_PREFIX, "prefix", rankData.getPrefix()));
        sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_INFO_SUFFIX, "suffix", rankData.getSuffix()));
        sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_INFO_WEIGHT, "weight", String.valueOf(rankData.getWeight())));

        if (playerRankData != null) {
            sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_INFO_EXPIRES, "expiry", playerRankData.getTimeRemaining()));
            sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_INFO_GIVEN, "given", playerRankData.getTimeSinceGiven()));
        } else {
            sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_INFO_EXPIRES, "expiry", "Permanent"));
            sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_INFO_GIVEN, "given", "Default"));
        }

        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Player names
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() != null && player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
