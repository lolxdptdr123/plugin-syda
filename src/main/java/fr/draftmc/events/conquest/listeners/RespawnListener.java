package fr.draftmc.events.conquest.listeners;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.ConquestState;
import fr.draftmc.events.conquest.model.Zone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Si le point de respawn tombe dans une zone, y (re)inscrit le joueur. */
public class RespawnListener implements Listener {

    private final ConquestPlugin plugin;

    public RespawnListener(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.getConquestManager().getState() != ConquestState.RUNNING) return;

        Player player = event.getPlayer();
        Zone zone = plugin.getZoneManager().getZoneAt(event.getRespawnLocation());
        if (zone != null) {
            plugin.getCaptureManager().onPlayerEnterZone(player, zone);
        }
    }
}
