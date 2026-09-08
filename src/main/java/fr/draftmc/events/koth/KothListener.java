package fr.draftmc.events.koth;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class KothListener implements Listener {
    private final KothPlugin plugin;

    public KothListener(KothPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player == null || !plugin.getKothManager().isRunning()) {
            return;
        }
        plugin.getKothManager().applyDeathPenalty(player);
    }
}
