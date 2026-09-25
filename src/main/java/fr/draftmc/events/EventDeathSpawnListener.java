package fr.draftmc.events;

import fr.draftmc.Draftmc;
import fr.draftmc.hub.SpawnCommand;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Toute mort → respawn au spawn général (/spawn set), peu importe le monde.
 */
public class EventDeathSpawnListener implements Listener {
    private final Draftmc plugin;

    public EventDeathSpawnListener(Draftmc plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Location spawn = SpawnCommand.resolve(plugin);
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }
}
