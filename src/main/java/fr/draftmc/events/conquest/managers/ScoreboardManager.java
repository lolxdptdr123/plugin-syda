package fr.draftmc.events.conquest.managers;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.Zone;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sidebar live : etat de chaque zone (proprietaire courant, verrouillage)
 * et total de points de la faction du joueur. Rafraichi chaque seconde.
 *
 * NOTE 1.8 : chaque ligne de scoreboard est en realite un "faux joueur"
 * limite a 16 caracteres visibles - les lignes sont tronquees en
 * consequence (limitation du protocole, pas du plugin).
 */
public class ScoreboardManager {

    private final ConquestPlugin plugin;
    private BukkitTask task;

    public ScoreboardManager(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), this::tick, 0L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(main);
        }
    }

    private void tick() {
        String title = plugin.getConfig().getString("scoreboard.title", "&6&lCONQUEST");
        int pointsToWin = plugin.getConfig().getInt("general.points-to-win", 100);

        for (Player player : Bukkit.getOnlinePlayers()) {
            String factionId = plugin.getEventFactionHook().getFactionId(player);
            int total = plugin.getConquestManager().getFactionTotal(factionId);

            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.GRAY + "Objectif: " + pointsToWin + "pts");
            lines.add(" ");

            for (Zone zone : plugin.getZoneManager().getZones().values()) {
                lines.add(buildZoneLine(zone, factionId));
            }

            lines.add("  ");
            lines.add(ChatColor.GOLD + "Total: " + total + "/" + pointsToWin);

            update(player, title, lines);
        }
    }

    /** N'affiche QUE les points de la faction du joueur qui regarde ce scoreboard, jamais ceux des autres factions. */
    private String buildZoneLine(Zone zone, String viewerFactionId) {
        int max = plugin.getConfig().getInt("general.zone-max-points", 25);
        int myPoints = zone.getPoints(viewerFactionId);

        return zone.getDisplayName() + ChatColor.GRAY + " " + myPoints + "/" + max;
    }

    private void update(Player player, String title, List<String> lines) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("conquest", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(truncate(ChatColor.translateAlternateColorCodes('&', title), 32));

        Set<String> used = new HashSet<>();
        int score = lines.size();
        for (String rawLine : lines) {
            String entry = truncate(rawLine, 16);
            while (used.contains(entry)) {
                entry = entry + ChatColor.RESET;
                if (entry.length() > 16) entry = entry.substring(0, 16);
            }
            used.add(entry);
            obj.getScore(entry).setScore(score);
            score--;
        }
        decorate(board, player);
        player.setScoreboard(board);
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private void decorate(Scoreboard board, Player player) {
        if (plugin.getHost().scoreboard() != null) {
            plugin.getHost().scoreboard().decorate(board, player);
        }
    }
}
