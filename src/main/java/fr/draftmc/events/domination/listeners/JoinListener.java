package fr.draftmc.events.domination.listeners;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.DominationState;
import fr.draftmc.events.domination.model.Zone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Si un joueur se reconnecte alors qu'il se trouve deja dans une zone (rare), l'y (re)inscrit proprement. */
public class JoinListener implements Listener {

    private final DominationPlugin plugin;

    public JoinListener(DominationPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getDominationManager().getState() != DominationState.RUNNING) return;

        Player player = event.getPlayer();
        Zone zone = plugin.getZoneManager().getZoneAt(player.getLocation());
        if (zone != null) {
            plugin.getScoringManager().onPlayerEnterZone(player, zone);
        }
    }
}
