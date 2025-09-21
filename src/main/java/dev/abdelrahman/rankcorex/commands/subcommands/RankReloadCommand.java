package dev.abdelrahman.rankcorex.commands.subcommands;

import dev.abdelrahman.rankcorex.Rankcorex;
import dev.abdelrahman.rankcorex.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RankReloadCommand {

    private final Rankcorex plugin;

    public RankReloadCommand(Rankcorex plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rankcorex.reload")) {
            sender.sendMessage(MessageUtils.colorize(MessageUtils.NO_PERMISSION));
            return true;
        }

        // Reload configuration
        plugin.reloadConfig();

        // Reload ranks
        plugin.getRankManager().loadRanks();

        // Reapply ranks to all online players
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            plugin.getRankManager().applyPlayerRank(onlinePlayer);
        }

        // Sync reload across network if enabled
        if (plugin.getSyncManager() != null) {
            plugin.getSyncManager().syncConfigReload();
        }

        sender.sendMessage(MessageUtils.colorize(MessageUtils.CONFIG_RELOADED));

        return true;
    }
}