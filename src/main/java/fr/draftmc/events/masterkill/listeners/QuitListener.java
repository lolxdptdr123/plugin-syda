package fr.draftmc.events.masterkill.listeners;

import fr.draftmc.events.masterkill.MasterKillPlugin;
import fr.draftmc.events.masterkill.model.MasterKillState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Une deconnexion pendant le match compte comme une elimination. */
public class QuitListener implements Listener {

    private final MasterKillPlugin plugin;

    public QuitListener(MasterKillPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getMasterKillManager().getState() != MasterKillState.RUNNING) return;
        plugin.getMasterKillManager().onPlayerEliminated(event.getPlayer(), null);
    }
}
