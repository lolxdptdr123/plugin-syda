package fr.draftmc.events.domination.listeners;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.DominationState;
import fr.draftmc.events.domination.model.Zone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Detecte l'entree/sortie d'un joueur dans une zone.
 * PERFORMANCE : ignore les micro-mouvements (camera/tete) qui ne changent
 * pas de bloc - seul un changement de bloc declenche une verification des
 * zones (peu nombreuses), ce qui reste tres leger meme a 200+ joueurs.
 */
public class MoveListener implements Listener {

    private final DominationPlugin plugin;

    public MoveListener(DominationPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (plugin.getDominationManager().getState() != DominationState.RUNNING) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Zone currentZone = findZoneContaining(player);
        Zone newZone = plugin.getZoneManager().getZoneAt(event.getTo());

        if (currentZone == newZone) return;

        if (currentZone != null) {
            plugin.getScoringManager().onPlayerLeaveZone(player, currentZone);
        }
        if (newZone != null) {
            plugin.getScoringManager().onPlayerEnterZone(player, newZone);
        }
    }

    private Zone findZoneContaining(Player player) {
        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            if (zone.getPlayersInside().contains(player.getUniqueId())) {
                return zone;
            }
        }
        return null;
    }
}
