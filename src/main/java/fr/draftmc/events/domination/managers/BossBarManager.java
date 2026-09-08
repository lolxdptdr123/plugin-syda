package fr.draftmc.events.domination.managers;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.DominationState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Affiche periodiquement le score total (points/points-to-win) de la
 * faction du joueur.
 *
 * NOTE TECHNIQUE : l'API Bukkit officielle org.bukkit.boss.BossBar n'existe
 * qu'a partir de la 1.9 - elle n'existe PAS dans l'API 1.8 ciblee par ce
 * plugin. Une "fausse" boss bar via entite (Wither/EnderDragon invisible)
 * necessiterait de desactiver son IA, ce qui n'est pas exposable par l'API
 * Bukkit 1.8 pure - seul du NMS profond et fragile le permettrait, ce qui
 * casserait la compatibilite multi-forks recherchee. Ce manager fournit
 * donc l'equivalent fonctionnel le plus fiable sur cette version : une
 * ligne de statut permanente via action bar, qui cede la priorite
 * d'affichage a ScoringManager pour un joueur precis des qu'il vient de
 * recevoir une ligne de zone (voir ScoringManager#tick()).
 */
public class BossBarManager {

    private final DominationPlugin plugin;
    private BukkitTask task;

    public BossBarManager(DominationPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("bossbar.enabled", true)) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (plugin.getDominationManager().getState() != DominationState.RUNNING) return;

        String format = plugin.getConfig().getString("bossbar.format",
                "&6Domination &7- &e{total}&7/&e{points-to-win} points");
        int pointsToWin = plugin.getConfig().getInt("general.points-to-win", 2000);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getScoringManager().wasShownZoneStatusThisTick(player)) continue;

            String factionId = plugin.getEventFactionHook().getFactionId(player);
            int total = plugin.getDominationManager().getFactionTotal(factionId);

            String line = format
                    .replace("{total}", String.valueOf(total))
                    .replace("{points-to-win}", String.valueOf(pointsToWin));
            plugin.getActionBarManager().send(player, line);
        }
    }
}
