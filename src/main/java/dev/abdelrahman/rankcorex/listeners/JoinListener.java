package dev.abdelrahman.rankcorex.listeners;

import dev.abdelrahman.rankcorex.Rankcorex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinListener implements Listener {

    private final Rankcorex plugin;

    public JoinListener(Rankcorex plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.debug("Player " + event.getPlayer().getName() + " joined, loading rank...");

        // Load player rank asynchronously
        plugin.getRankManager().loadPlayerRank(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.debug("Player " + event.getPlayer().getName() + " left, cleaning up...");

        // Clean up player data
        plugin.getRankManager().removePlayer(event.getPlayer());
    }
}