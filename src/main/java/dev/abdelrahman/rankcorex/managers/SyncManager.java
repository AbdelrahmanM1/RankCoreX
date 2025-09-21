package dev.abdelrahman.rankcorex.managers;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.abdelrahman.rankcorex.Rankcorex;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.UUID;

public class SyncManager implements PluginMessageListener {

    private final Rankcorex plugin;
    private final String channel;
    private final boolean notifySync;

    public SyncManager(Rankcorex plugin) {
        this.plugin = plugin;
        this.channel = plugin.getConfig().getString("sync.channel", "rankcorex:sync");
        this.notifySync = plugin.getConfig().getBoolean("sync.notify", true);
    }

    public boolean initialize() {
        if (!plugin.getConfig().getBoolean("global-sync", false)) {
            plugin.debug("Global sync is disabled");
            return false;
        }

        if (!plugin.getConfig().getString("storage.type", "yaml").equalsIgnoreCase("mysql")) {
            plugin.error("Global sync requires MySQL storage type!");
            return false;
        }

        // Register plugin messaging channel
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
        Bukkit.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, this);

        plugin.log("Cross-server sync initialized on channel: " + channel);
        return true;
    }

    public void syncRankChange(UUID playerId, String playerName, String rankName, String timeExpires) {
        if (!plugin.getConfig().getBoolean("global-sync", false)) {
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("RANK_SET");
        out.writeUTF(playerId.toString());
        out.writeUTF(playerName);
        out.writeUTF(rankName);
        out.writeUTF(timeExpires != null ? timeExpires : "PERMANENT");

        sendPluginMessage(out.toByteArray());

        if (notifySync) {
            plugin.log("Synced rank change: " + playerName + " -> " + rankName);
        }
    }

    public void syncRankRemoval(UUID playerId, String playerName) {
        if (!plugin.getConfig().getBoolean("global-sync", false)) {
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("RANK_REMOVE");
        out.writeUTF(playerId.toString());
        out.writeUTF(playerName);

        sendPluginMessage(out.toByteArray());

        if (notifySync) {
            plugin.log("Synced rank removal: " + playerName);
        }
    }

    public void syncConfigReload() {
        if (!plugin.getConfig().getBoolean("global-sync", false)) {
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("CONFIG_RELOAD");

        sendPluginMessage(out.toByteArray());

        if (notifySync) {
            plugin.log("Synced config reload across network");
        }
    }

    private void sendPluginMessage(byte[] data) {
        // Get any online player to send the message
        Player messenger = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            messenger = player;
            break;
        }

        if (messenger != null) {
            messenger.sendPluginMessage(plugin, channel, data);
            plugin.debug("Sent plugin message via player: " + messenger.getName());
        } else {
            plugin.debug("No online players available to send plugin message");
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!this.channel.equals(channel)) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String action = in.readUTF();

        plugin.debug("Received sync message: " + action);

        switch (action) {
            case "RANK_SET":
                handleRankSetSync(in);
                break;
            case "RANK_REMOVE":
                handleRankRemoveSync(in);
                break;
            case "CONFIG_RELOAD":
                handleConfigReloadSync();
                break;
            default:
                plugin.debug("Unknown sync action: " + action);
        }
    }

    private void handleRankSetSync(ByteArrayDataInput in) {
        try {
            String playerIdStr = in.readUTF();
            String playerName = in.readUTF();
            String rankName = in.readUTF();
            String timeExpires = in.readUTF();

            if ("PERMANENT".equals(timeExpires)) {
                timeExpires = null;
            }

            UUID playerId = UUID.fromString(playerIdStr);

            // Update local cache and apply to online player
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getRankManager().getPlayerRank(playerId); // This will reload from database

                Player onlinePlayer = Bukkit.getPlayer(playerId);
                if (onlinePlayer != null) {
                    plugin.getRankManager().loadPlayerRank(onlinePlayer);
                }
            });

            if (notifySync) {
                plugin.log("Applied synced rank change: " + playerName + " -> " + rankName);
            }

        } catch (Exception e) {
            plugin.error("Failed to handle rank set sync: " + e.getMessage());
        }
    }

    private void handleRankRemoveSync(ByteArrayDataInput in) {
        try {
            String playerIdStr = in.readUTF();
            String playerName = in.readUTF();

            UUID playerId = UUID.fromString(playerIdStr);

            // Update local cache and apply to online player
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player onlinePlayer = Bukkit.getPlayer(playerId);
                if (onlinePlayer != null) {
                    plugin.getRankManager().loadPlayerRank(onlinePlayer);
                }
            });

            if (notifySync) {
                plugin.log("Applied synced rank removal: " + playerName);
            }

        } catch (Exception e) {
            plugin.error("Failed to handle rank remove sync: " + e.getMessage());
        }
    }

    private void handleConfigReloadSync() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.reloadConfig();
            plugin.getRankManager().loadRanks();

            // Reapply ranks to all online players
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                plugin.getRankManager().applyPlayerRank(onlinePlayer);
            }
        });

        if (notifySync) {
            plugin.log("Applied synced config reload");
        }
    }

    public void shutdown() {
        if (plugin.getConfig().getBoolean("global-sync", false)) {
            Bukkit.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, channel);
            Bukkit.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
            plugin.debug("Unregistered sync channels");
        }
    }
}