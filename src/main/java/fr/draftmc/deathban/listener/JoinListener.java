package fr.draftmc.deathban.listener;

import fr.draftmc.deathban.DeathBanPlugin;
import fr.draftmc.deathban.manager.DeathBanEntry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class JoinListener implements Listener {

    private final DeathBanPlugin plugin;

    public JoinListener(DeathBanPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        DeathBanEntry entry = plugin.getBanManager().getBan(player.getUniqueId());
        if (entry == null) {
            return;
        }
        if (player.hasPermission("deathban.bypass.teleport")) {
            return;
        }
        if (!plugin.getBanManager().isBannedFrom(player.getUniqueId(), player.getWorld().getName())) {
            return;
        }

        String duration = plugin.getBanManager().formatDuration(entry);
        player.sendMessage(plugin.getConfigManager().message("already-banned")
                .replace("{world}", entry.getWorld())
                .replace("{duration}", duration));
        plugin.getBanManager().sendToSafeWorld(player);
    }
}
