package fr.draftmc.events.conquest.listeners;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.ConquestState;
import fr.draftmc.events.conquest.model.Zone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Si un joueur se reconnecte alors qu'il se trouve deja dans une zone (rare), l'y (re)inscrit proprement. */
public class JoinListener implements Listener {

    private final ConquestPlugin plugin;

    public JoinListener(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConquestManager().getState() != ConquestState.RUNNING) return;

        Player player = event.getPlayer();
        Zone zone = plugin.getZoneManager().getZoneAt(player.getLocation());
        if (zone != null) {
            plugin.getCaptureManager().onPlayerEnterZone(player, zone);
        }
    }
}
