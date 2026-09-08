package fr.draftmc.events.conquest.listeners;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.ConquestState;
import fr.draftmc.events.conquest.model.Zone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Une teleportation quitte immediatement la capture en cours, et rejoint une zone si la destination en fait partie. */
public class TeleportListener implements Listener {

    private final ConquestPlugin plugin;

    public TeleportListener(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.getConquestManager().getState() != ConquestState.RUNNING) return;

        Player player = event.getPlayer();
        plugin.getCaptureManager().onPlayerRemoved(player);

        if (event.getTo() != null) {
            Zone newZone = plugin.getZoneManager().getZoneAt(event.getTo());
            if (newZone != null) {
                plugin.getCaptureManager().onPlayerEnterZone(player, newZone);
            }
        }
    }
}
