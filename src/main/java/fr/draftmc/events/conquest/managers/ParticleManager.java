package fr.draftmc.events.conquest.managers;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

/**
 * Joue un effet Bukkit (org.bukkit.Effect, API 1.8) en boucle au centre de
 * chaque zone non verrouillee, pour la rendre visuellement reperable.
 */
public class ParticleManager {

    private final ConquestPlugin plugin;
    private BukkitTask task;

    public ParticleManager(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("particles.enabled", true)) return;
        long interval = plugin.getConfig().getLong("particles.interval-ticks", 40);
        task = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        Effect effect;
        try {
            effect = Effect.valueOf(plugin.getConfig().getString("particles.effect", "FIREWORKS_SPARK"));
        } catch (IllegalArgumentException e) {
            return;
        }

        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            if (zone.isLocked()) continue;
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) continue;

            Location center = zone.getCenter(world);
            world.playEffect(center, effect, 0);
        }
    }
}
