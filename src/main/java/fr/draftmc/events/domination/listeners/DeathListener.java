package fr.draftmc.events.domination.listeners;

import fr.draftmc.events.EventMonthStats;
import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.DominationState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Une mort retire immediatement le joueur des zones et applique la penalite de points. */
public class DeathListener implements Listener {

    private final DominationPlugin plugin;

    public DeathListener(DominationPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.getDominationManager().getState() != DominationState.RUNNING) return;

        Player player = event.getEntity();
        plugin.getScoringManager().onPlayerRemoved(player);
        plugin.getDominationManager().applyDeathPenalty(player);
        EventMonthStats.add(player, EventMonthStats.DOM_DEATHS, 1);
    }
}
