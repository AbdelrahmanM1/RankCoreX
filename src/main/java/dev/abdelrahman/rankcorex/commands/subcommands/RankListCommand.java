package dev.abdelrahman.rankcorex.commands.subcommands;

import dev.abdelrahman.rankcorex.Rankcorex;
import dev.abdelrahman.rankcorex.models.RankData;
import dev.abdelrahman.rankcorex.utils.MessageUtils;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RankListCommand {

    private final Rankcorex plugin;

    public RankListCommand(Rankcorex plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rankcorex.list")) {
            sender.sendMessage(MessageUtils.colorize(MessageUtils.NO_PERMISSION));
            return true;
        }

        Collection<RankData> ranks = plugin.getRankManager().getAllRanks();

        if (ranks.isEmpty()) {
            sender.sendMessage(MessageUtils.colorize(MessageUtils.NO_RANKS_DEFINED));
            return true;
        }

        sender.sendMessage(MessageUtils.colorize(MessageUtils.RANK_LIST_HEADER));

        // Sort ranks by weight (descending)
        ranks.stream()
                .sorted((r1, r2) -> Integer.compare(r2.getWeight(), r1.getWeight()))
                .forEach(rank -> {
                    String defaultMarker = rank.isDefault() ? " &a(Default)" : "";
                    sender.sendMessage(MessageUtils.replacePlaceholders(MessageUtils.RANK_LIST_ITEM + defaultMarker,
                            "rank", rank.getName(),
                            "weight", String.valueOf(rank.getWeight()),
                            "prefix", rank.getPrefix()));
                });

        return true;
    }
}