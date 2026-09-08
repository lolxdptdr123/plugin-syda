package fr.draftmc.deathban.listener;

import fr.draftmc.deathban.DeathBanPlugin;
import fr.draftmc.deathban.manager.DeathBanEntry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class TeleportListener implements Listener {

    private final DeathBanPlugin plugin;

    public TeleportListener(DeathBanPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getFrom() != null
                && event.getFrom().getWorld() != null
                && event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        if (event.getPlayer().hasPermission("deathban.bypass.teleport")) {
            return;
        }
        String worldName = event.getTo().getWorld().getName();
        if (!plugin.getBanManager().isBannedFrom(event.getPlayer().getUniqueId(), worldName)) {
            return;
        }
        event.setCancelled(true);
        DeathBanEntry entry = plugin.getBanManager().getBan(event.getPlayer().getUniqueId());
        event.getPlayer().sendMessage(plugin.getConfigManager().message("teleport-denied")
                .replace("{world}", worldName)
                .replace("{duration}", plugin.getBanManager().formatDuration(entry)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getPlayer().hasPermission("deathban.bypass.teleport")) {
            return;
        }
        String worldName = event.getTo().getWorld().getName();
        if (!plugin.getBanManager().isBannedFrom(event.getPlayer().getUniqueId(), worldName)) {
            return;
        }
        event.setCancelled(true);
        DeathBanEntry entry = plugin.getBanManager().getBan(event.getPlayer().getUniqueId());
        event.getPlayer().sendMessage(plugin.getConfigManager().message("teleport-denied")
                .replace("{world}", worldName)
                .replace("{duration}", plugin.getBanManager().formatDuration(entry)));
    }
}
