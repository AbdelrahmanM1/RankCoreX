package dev.abdelrahman.rankcorex.commands;

import dev.abdelrahman.rankcorex.Rankcorex;
import dev.abdelrahman.rankcorex.commands.subcommands.*;
import dev.abdelrahman.rankcorex.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RankCommand implements CommandExecutor, TabCompleter {

    private final Rankcorex plugin;
    private final RankSetCommand setCommand;
    private final RankRemoveCommand removeCommand;
    private final RankCheckCommand checkCommand;
    private final RankListCommand listCommand;
    private final RankReloadCommand reloadCommand;

    public RankCommand(Rankcorex plugin) {
        this.plugin = plugin;
        this.setCommand = new RankSetCommand(plugin);
        this.removeCommand = new RankRemoveCommand(plugin);
        this.checkCommand = new RankCheckCommand(plugin);
        this.listCommand = new RankListCommand(plugin);
        this.reloadCommand = new RankReloadCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MessageUtils.colorize(MessageUtils.USAGE_MAIN));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            case "set":
                return setCommand.execute(sender, subArgs);
            case "remove":
            case "rem":
                return removeCommand.execute(sender, subArgs);
            case "check":
            case "info":
                return checkCommand.execute(sender, subArgs);
            case "list":
                return listCommand.execute(sender, subArgs);
            case "reload":
                return reloadCommand.execute(sender, subArgs);
            default:
                sender.sendMessage(MessageUtils.colorize(MessageUtils.USAGE_MAIN));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommands
            List<String> subCommands = Arrays.asList("set", "remove", "check", "list", "reload");
            for (String subCommand : subCommands) {
                if (subCommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length >= 2) {
            // Delegate to subcommand tab completion
            String subCommand = args[0].toLowerCase();
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

            switch (subCommand) {
                case "set":
                    return setCommand.tabComplete(sender, subArgs);
                case "remove":
                case "rem":
                    return removeCommand.tabComplete(sender, subArgs);
                case "check":
                case "info":
                    return checkCommand.tabComplete(sender, subArgs);
                default:
                    return completions;
            }
        }

        return completions;
    }
}
