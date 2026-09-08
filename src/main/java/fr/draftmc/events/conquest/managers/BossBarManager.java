package fr.draftmc.events.conquest.managers;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.ConquestState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Affiche periodiquement le score total (points/points-to-win) de la
 * faction du joueur.
 *
 * NOTE TECHNIQUE IMPORTANTE : l'API Bukkit officielle org.bukkit.boss.BossBar
 * n'existe qu'a partir de la 1.9 - elle n'existe PAS dans l'API 1.8 ciblee
 * par ce plugin. Une "fausse" boss bar via entite (Wither/EnderDragon
 * invisible) necessiterait de desactiver son IA et son animation de spawn,
 * ce qui n'est PAS exposable via l'API Bukkit 1.8 pure (LivingEntity#setAI
 * n'existe pas non plus avant 1.9) - seul du NMS profond et fragile le
 * permettrait, ce qui casserait la compatibilite multi-forks recherchee.
 * Ce manager fournit donc l'equivalent fonctionnel le plus fiable possible
 * sur cette version : une ligne de statut permanente via action bar, qui
 * cede la priorite d'affichage a CaptureManager pour un joueur precis des
 * qu'il vient de recevoir une ligne de capture (parce qu'il est present
 * dans une zone activement capturee - voir CaptureManager#tick()).
 */
public class BossBarManager {

    private final ConquestPlugin plugin;
    private BukkitTask task;

    public BossBarManager(ConquestPlugin plugin) {
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
        if (plugin.getConquestManager().getState() != ConquestState.RUNNING) return;

        String format = plugin.getConfig().getString("bossbar.format",
                "&6Conquest &7- &e{total}&7/&e{points-to-win} points");
        int pointsToWin = plugin.getConfig().getInt("general.points-to-win", 100);

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Ce joueur precis vient de recevoir la ligne de capture de sa
            // zone cette meme seconde : on ne l'ecrase pas avec le total.
            if (plugin.getCaptureManager().wasShownCaptureThisTick(player)) continue;

            String factionId = plugin.getEventFactionHook().getFactionId(player);
            int total = plugin.getConquestManager().getFactionTotal(factionId);

            String line = format
                    .replace("{total}", String.valueOf(total))
                    .replace("{points-to-win}", String.valueOf(pointsToWin));
            plugin.getActionBarManager().send(player, line);
        }
    }
}
