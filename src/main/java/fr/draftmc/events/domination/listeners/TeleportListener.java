package fr.draftmc.events.domination.listeners;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.DominationState;
import fr.draftmc.events.domination.model.Zone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Une teleportation retire immediatement le joueur de sa zone actuelle, et le rejoint a une zone si la destination en fait partie. */
public class TeleportListener implements Listener {

    private final DominationPlugin plugin;

    public TeleportListener(DominationPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.getDominationManager().getState() != DominationState.RUNNING) return;

        Player player = event.getPlayer();
        plugin.getScoringManager().onPlayerRemoved(player);

        if (event.getTo() != null) {
            Zone newZone = plugin.getZoneManager().getZoneAt(event.getTo());
            if (newZone != null) {
                plugin.getScoringManager().onPlayerEnterZone(player, newZone);
            }
        }
    }
}
