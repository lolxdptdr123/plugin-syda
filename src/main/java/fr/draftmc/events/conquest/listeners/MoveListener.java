package fr.draftmc.events.conquest.listeners;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.ConquestState;
import fr.draftmc.events.conquest.model.Zone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Detecte l'entree/sortie d'un joueur dans une zone de capture.
 * PERFORMANCE : ignore les micro-mouvements (camera/tete) qui ne changent
 * pas de bloc - seul un changement de bloc declenche une verification des
 * zones (peu nombreuses), ce qui reste tres leger meme a 200+ joueurs.
 */
public class MoveListener implements Listener {

    private final ConquestPlugin plugin;

    public MoveListener(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (plugin.getConquestManager().getState() != ConquestState.RUNNING) return;

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
            plugin.getCaptureManager().onPlayerLeaveZone(player, currentZone);
        }
        if (newZone != null) {
            plugin.getCaptureManager().onPlayerEnterZone(player, newZone);
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
