package fr.draftmc.events.conquest.managers;

import fr.draftmc.events.conquest.ConquestPlugin;
import fr.draftmc.events.conquest.model.ConquestState;
import fr.draftmc.events.conquest.model.Zone;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Plus de sidebar separee : les lignes sont injectees dans le scoreboard Draftmc.
 */
public class ScoreboardManager {

    private final ConquestPlugin plugin;
    private BukkitTask task;

    public ScoreboardManager(ConquestPlugin plugin) {
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
        ConquestState state = plugin.getConquestManager().getState();
        return state == ConquestState.RUNNING || state == ConquestState.STARTING;
    }

    public List<String> linesFor(Player player) {
        List<String> lines = new ArrayList<String>();
        ConquestManager mgr = plugin.getConquestManager();
        if (!active()) {
            return lines;
        }
        if (mgr.getState() == ConquestState.STARTING) {
            lines.add(ChatColor.GOLD + "Conquest " + ChatColor.YELLOW + mgr.getCountdownSecondsLeft() + "s");
        } else {
            lines.add(ChatColor.GOLD + "Conquest");
        }
        addTopLines(lines);
        String factionId = plugin.getEventFactionHook().getFactionId(player);
        addZoneGrid(lines, factionId);
        return lines;
    }

    private void addTopLines(List<String> lines) {
        List<Map.Entry<String, Integer>> ranked = new ArrayList<Map.Entry<String, Integer>>(
                plugin.getConquestManager().getAllTotals().entrySet());
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
        int max = plugin.getConfig().getInt("general.zone-max-points", 25);
        List<Zone> zones = new ArrayList<Zone>(plugin.getZoneManager().getZones().values());
        if (zones.size() == 4) {
            lines.add(pair(zones.get(0), zones.get(2), factionId, max));
            lines.add(pair(zones.get(1), zones.get(3), factionId, max));
            return;
        }
        for (int i = 0; i < zones.size(); i += 2) {
            if (i + 1 < zones.size()) {
                lines.add(pair(zones.get(i), zones.get(i + 1), factionId, max));
            } else {
                lines.add(cell(zones.get(i), factionId, max));
            }
        }
    }

    private String pair(Zone left, Zone right, String factionId, int max) {
        return cell(left, factionId, max) + " " + cell(right, factionId, max);
    }

    private String cell(Zone zone, String factionId, int max) {
        int pts = zone.getPoints(factionId);
        return zone.getColor() + zone.getName() + ChatColor.GRAY + " " + pts + "/" + max;
    }
}
