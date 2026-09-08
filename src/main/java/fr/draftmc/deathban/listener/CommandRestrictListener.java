package fr.draftmc.deathban.listener;

import fr.draftmc.deathban.DeathBanPlugin;
import fr.draftmc.deathban.manager.ConfigManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class CommandRestrictListener implements Listener {

    private final DeathBanPlugin plugin;

    public CommandRestrictListener(DeathBanPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().isDeathWorld(event.getPlayer().getWorld())) {
            return;
        }
        if (event.getPlayer().hasPermission("deathban.bypass.commands")) {
            return;
        }

        String message = event.getMessage();
        if (message == null || message.length() < 2) {
            return;
        }
        String withoutSlash = message.substring(1).trim();
        String label = withoutSlash.split(" ")[0];
        if (plugin.getConfigManager().isCommandAllowed(ConfigManager.normalizeCommand(label))) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.getConfigManager().message("command-denied"));
    }
}
