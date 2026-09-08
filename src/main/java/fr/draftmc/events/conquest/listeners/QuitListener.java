package fr.draftmc.events.conquest.listeners;

import fr.draftmc.events.conquest.ConquestPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Une deconnexion retire immediatement le joueur de toute zone qu'il capturait. */
public class QuitListener implements Listener {

    private final ConquestPlugin plugin;

    public QuitListener(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getCaptureManager().onPlayerRemoved(event.getPlayer());
    }
}
