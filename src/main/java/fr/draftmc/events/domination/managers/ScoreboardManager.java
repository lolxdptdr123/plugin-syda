package fr.draftmc.events.domination.managers;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.DominationState;
import fr.draftmc.events.domination.model.Zone;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plus de sidebar séparée : les lignes sont injectées dans le scoreboard Draftmc
 * (même principe que Conquest), à la place de la section Infos.
 */
public class ScoreboardManager {

    private final DominationPlugin plugin;
    private BukkitTask task;

    public ScoreboardManager(DominationPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean active() {
        DominationState state = plugin.getDominationManager().getState();
        return state == DominationState.RUNNING || state == DominationState.STARTING;
    }

    public List<String> linesFor(Player player) {
        List<String> lines = new ArrayList<String>();
        if (!active()) {
            return lines;
        }
        DominationManager mgr = plugin.getDominationManager();
        int pointsToWin = plugin.getConfig().getInt("general.points-to-win", 2000);
        String factionId = plugin.getEventFactionHook().getFactionId(player);
        int total = mgr.getFactionTotal(factionId);

        if (mgr.getState() == DominationState.STARTING) {
            lines.add(ChatColor.GOLD + "Domination " + ChatColor.YELLOW + mgr.getCountdownSecondsLeft() + "s");
        } else {
            lines.add(ChatColor.GOLD + "Domination " + ChatColor.GRAY + total + "/" + pointsToWin);
        }
        addTopLines(lines);
        addZoneGrid(lines, factionId);
        return lines;
    }

    private void addTopLines(List<String> lines) {
        List<Map.Entry<String, Integer>> ranked = new ArrayList<Map.Entry<String, Integer>>();
        for (Map.Entry<String, Integer> entry : plugin.getDominationManager().getAllTotals().entrySet()) {
            if (entry.getValue() != null && entry.getValue().intValue() > 0) {
                ranked.add(entry);
            }
        }
        Collections.sort(ranked, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue().intValue() - a.getValue().intValue();
            }
        });
        for (int i = 0; i < 3; i++) {
            if (i < ranked.size()) {
                Map.Entry<String, Integer> entry = ranked.get(i);
                String name = plugin.getEventFactionHook().getFactionDisplayName(entry.getKey());
                name = ChatColor.stripColor(name);
                if (name.length() > 8) {
                    name = name.substring(0, 8);
                }
                lines.add(ChatColor.YELLOW + "#" + (i + 1) + " " + ChatColor.WHITE + name
                        + ChatColor.GRAY + " " + entry.getValue());
            } else {
                lines.add(ChatColor.GRAY + "#" + (i + 1) + " -");
            }
        }
    }

    private void addZoneGrid(List<String> lines, String factionId) {
        List<Zone> zones = new ArrayList<Zone>(plugin.getZoneManager().getZones().values());
        if (zones.size() == 4) {
            lines.add(pair(zones.get(0), zones.get(2), factionId));
            lines.add(pair(zones.get(1), zones.get(3), factionId));
            return;
        }
        for (int i = 0; i < zones.size(); i += 2) {
            if (i + 1 < zones.size()) {
                lines.add(pair(zones.get(i), zones.get(i + 1), factionId));
            } else {
                lines.add(cell(zones.get(i), factionId));
            }
        }
    }

    private String pair(Zone left, Zone right, String factionId) {
        return cell(left, factionId) + " " + cell(right, factionId);
    }

    private String cell(Zone zone, String factionId) {
        boolean scoring = isFactionPresent(zone, factionId);
        ChatColor marker = scoring ? ChatColor.GREEN : ChatColor.DARK_GRAY;
        int pts = zone.getPoints(factionId);
        return marker + "■" + zone.getColor() + zone.getName() + ChatColor.GRAY + " " + pts;
    }

    private boolean isFactionPresent(Zone zone, String factionId) {
        if (factionId == null) {
            return false;
        }
        for (UUID uuid : zone.getPlayersInside()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && factionId.equals(plugin.getEventFactionHook().getFactionId(p))) {
                return true;
            }
        }
        return false;
    }
}
