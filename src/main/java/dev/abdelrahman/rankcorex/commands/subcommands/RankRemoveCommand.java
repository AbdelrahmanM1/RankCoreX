package dev.abdelrahman.rankcorex.commands.subcommands;

import dev.abdelrahman.rankcorex.Rankcorex;
import dev.abdelrahman.rankcorex.models.PlayerRankData;
import dev.abdelrahman.rankcorex.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class RankRemoveCommand {

    private final Rankcorex plugin;

    public RankRemoveCommand(Rankcorex plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rankcorex.remove")) {
            sender.sendMessage(MessageUtils.colorize(MessageUtils.NO_PERMISSION));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(MessageUtils.colorize(MessageUtils.USAGE_REMOVE));
            return true;
        }

        String playerName = args[0];

        // Get player (offline or online)
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.PLAYER_NOT_FOUND, "player", playerName));
            return true;
        }

        // Check if player has a rank to remove
        PlayerRankData currentRank = plugin.getRankManager().getPlayerRank(target.getUniqueId());
        if (currentRank == null) {
            sender.sendMessage(MessageUtils.colorize("&cPlayer " + target.getName() + " has no rank to remove."));
            return true;
        }

        // Remove the rank
        plugin.getRankManager().removePlayerRank(target.getUniqueId());

        // Send success message
        sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_REMOVED,
                "player", target.getName(), "rank", currentRank.getRankName()));

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