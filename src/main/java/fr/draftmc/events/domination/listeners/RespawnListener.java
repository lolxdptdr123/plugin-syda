package fr.draftmc.events.domination.listeners;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.DominationState;
import fr.draftmc.events.domination.model.Zone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Si le point de respawn tombe dans une zone, y (re)inscrit le joueur. */
public class RespawnListener implements Listener {

    private final DominationPlugin plugin;

    public RespawnListener(DominationPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.getDominationManager().getState() != DominationState.RUNNING) return;

        Player player = event.getPlayer();
        Zone zone = plugin.getZoneManager().getZoneAt(event.getRespawnLocation());
        if (zone != null) {
            plugin.getScoringManager().onPlayerEnterZone(player, zone);
        }
    }
}
