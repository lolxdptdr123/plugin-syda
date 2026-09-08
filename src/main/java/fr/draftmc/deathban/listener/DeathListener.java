package fr.draftmc.deathban.listener;

import fr.draftmc.deathban.DeathBanPlugin;
import fr.draftmc.deathban.manager.DeathBanEntry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class DeathListener implements Listener {

    private final DeathBanPlugin plugin;

    public DeathListener(DeathBanPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!plugin.getConfigManager().isDeathWorld(player.getWorld())) {
            return;
        }
        if (player.hasPermission("deathban.bypass.ban")) {
            return;
        }

        DeathBanEntry entry = plugin.getBanManager().ban(player, player.getWorld().getName());
        String duration = plugin.getBanManager().formatDuration(entry);
        player.sendMessage(plugin.getConfigManager().message("death-banned")
                .replace("{world}", player.getWorld().getName())
                .replace("{duration}", duration));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DeathBanEntry entry = plugin.getBanManager().getBan(player.getUniqueId());
        if (entry == null) {
            return;
        }
        if (event.getRespawnLocation() != null
                && plugin.getBanManager().isBannedFrom(player.getUniqueId(), event.getRespawnLocation().getWorld().getName())) {
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(plugin.getConfigManager().getSafeWorld());
            if (world == null && !org.bukkit.Bukkit.getWorlds().isEmpty()) {
                world = org.bukkit.Bukkit.getWorlds().get(0);
            }
            if (world != null) {
                event.setRespawnLocation(world.getSpawnLocation());
            }
        }
    }
}
