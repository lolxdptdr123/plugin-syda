package fr.draftmc.events.domination.managers;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integration OPTIONNELLE avec HolographicDisplays, detectee par
 * reflexion (aucune dependance de compilation). Si le plugin n'est pas
 * installe ou desactive, ce manager reste simplement inactif : Domination
 * continue de fonctionner parfaitement sans hologrammes.
 *
 * Contrairement a Conquest (une seule faction "en tete" par zone),
 * Domination liste TOUTES les factions ayant des points sur la zone,
 * puisque plusieurs peuvent controler simultanement.
 */
public class HologramManager {

    private final DominationPlugin plugin;
    private final Map<String, Object> holograms = new HashMap<>(); // nom de zone -> instance Hologram
    private BukkitTask task;

    private boolean available = false;
    private Method createHologramMethod;
    private Method appendTextLineMethod;
    private Method clearLinesMethod;
    private Method deleteMethod;

    public HologramManager(DominationPlugin plugin) {
        this.plugin = plugin;
        detect();
    }

    private void detect() {
        Plugin hd = Bukkit.getPluginManager().getPlugin("HolographicDisplays");
        if (hd == null || !hd.isEnabled()) return;

        try {
            Class<?> apiClass = Class.forName("com.gmail.filoghost.holographicdisplays.api.HologramsAPI");
            createHologramMethod = apiClass.getMethod("createHologram", Plugin.class, Location.class);

            Class<?> hologramClass = Class.forName("com.gmail.filoghost.holographicdisplays.api.Hologram");
            appendTextLineMethod = hologramClass.getMethod("appendTextLine", String.class);
            clearLinesMethod = hologramClass.getMethod("clearLines");
            deleteMethod = hologramClass.getMethod("delete");

            available = true;
            plugin.getLogger().info("[Domination] HolographicDisplays detecte : hologrammes de zone actives.");
        } catch (Exception e) {
            available = false;
        }
    }

    public void start() {
        stop();
        if (!available || !plugin.getConfig().getBoolean("hologram.enabled", true)) return;

        double offset = plugin.getConfig().getDouble("hologram.height-offset", 2.5);
        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) continue;

            Location loc = zone.getCenter(world).add(0, offset, 0);
            try {
                Object hologram = createHologramMethod.invoke(null, plugin, loc);
                holograms.put(zone.getName(), hologram);
            } catch (Exception ignored) {
            }
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), this::tick, 0L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Object hologram : holograms.values()) {
            try {
                deleteMethod.invoke(hologram);
            } catch (Exception ignored) {
            }
        }
        holograms.clear();
    }

    private void tick() {
        for (Zone zone : plugin.getZoneManager().getZones().values()) {
            Object hologram = holograms.get(zone.getName());
            if (hologram == null) continue;

            try {
                clearLinesMethod.invoke(hologram);
                appendTextLineMethod.invoke(hologram, zone.getDisplayName());

                List<Map.Entry<String, Integer>> sorted = new ArrayList<>(zone.getAllPoints().entrySet());
                sorted.removeIf(e -> e.getValue() <= 0);
                sorted.sort((a, b) -> b.getValue() - a.getValue());

                if (sorted.isEmpty()) {
                    appendTextLineMethod.invoke(hologram, "§7Neutre");
                } else {
                    int shown = 0;
                    for (Map.Entry<String, Integer> entry : sorted) {
                        if (shown >= 5) break;
                        String name = plugin.getEventFactionHook().getFactionDisplayName(entry.getKey());
                        appendTextLineMethod.invoke(hologram, "§f" + name + " §7: §e" + entry.getValue());
                        shown++;
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}
