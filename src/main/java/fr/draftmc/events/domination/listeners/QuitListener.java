package fr.draftmc.events.domination.listeners;

import fr.draftmc.events.domination.DominationPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Une deconnexion retire immediatement le joueur de toute zone dans laquelle il se trouvait. */
public class QuitListener implements Listener {

    private final DominationPlugin plugin;

    public QuitListener(DominationPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getScoringManager().onPlayerRemoved(event.getPlayer());
    }
}
