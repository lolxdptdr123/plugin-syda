package fr.draftmc.events.conquest.listeners;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.ConquestState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Une mort libere immediatement la zone capturee et applique la penalite de points. */
public class DeathListener implements Listener {

    private final ConquestPlugin plugin;

    public DeathListener(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.getConquestManager().getState() != ConquestState.RUNNING) return;

        Player player = event.getEntity();
        plugin.getCaptureManager().onPlayerRemoved(player);
        plugin.getConquestManager().applyDeathPenalty(player);
    }
}
