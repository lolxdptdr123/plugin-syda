package fr.draftmc.events.masterkill.listeners;

import fr.draftmc.events.masterkill.MasterKillPlugin;
import fr.draftmc.events.masterkill.model.MasterKillState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Une mort declenche le comptage de kill (si assassin d'une autre faction) et la verification d'elimination/victoire. */
public class DeathListener implements Listener {

    private final MasterKillPlugin plugin;

    public DeathListener(MasterKillPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.getMasterKillManager().getState() != MasterKillState.RUNNING) return;

        if (plugin.getConfig().getBoolean("general.prevent-item-drop", true)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        plugin.getMasterKillManager().onPlayerEliminated(victim, killer);
    }
}
