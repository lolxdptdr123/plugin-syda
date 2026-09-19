package fr.draftmc.events.largage;

import fr.draftmc.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

public class LargageScoreboard {
    private static final String SEPARATOR = "&f&m--------------------";
    private final LargagePlugin plugin;
    private BukkitTask task;

    public LargageScoreboard(LargagePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!plugin.getManager().running()) {
            return;
        }
        String title = plugin.getConfig().getString("scoreboard.title",
                plugin.getHost().getConfig().getString("scoreboard.title", "&c✺ &6Draftmc.fr &c✺"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player, title, buildLines());
        }
    }

    private List<String> buildLines() {
        List<String> lines = new ArrayList<String>();
        LargageManager mgr = plugin.getManager();
        lines.add(CC.color(SEPARATOR));
        lines.add(CC.color("&fEvent : &6Largage"));
        if (!mgr.dropped()) {
            lines.add(CC.color("&fSpawn : &e" + format(mgr.countdown())));
        } else {
            lines.add(CC.color("&fCoffres : &e" + mgr.remainingChests()));
            lines.add(CC.color("&fTemps : &e" + format(mgr.timeLeft())));
        }
        lines.add(CC.color(SEPARATOR));
        return lines;
    }

    private String format(int sec) {
        int m = Math.max(0, sec) / 60;
        int s = Math.max(0, sec) % 60;
        return m + "m " + s + "s";
    }

    private void update(Player player, String title, List<String> lines) {
        Scoreboard board = player.getScoreboard();
        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()
                || board.getObjective("largage") == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }
        Objective obj = board.getObjective("largage");
        if (obj == null) {
            obj = board.registerNewObjective("largage", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        obj.setDisplayName(truncate(CC.color(title), 32));
        int score = lines.size();
        int index = 0;
        for (String rawLine : lines) {
            String entry = invisibleEntry(index++);
            String[] parts = split(rawLine, 16);
            String teamName = "l" + index;
            Team lineTeam = board.getTeam(teamName);
            if (lineTeam == null) {
                lineTeam = board.registerNewTeam(teamName);
            }
            if (!lineTeam.hasEntry(entry)) {
                lineTeam.addEntry(entry);
            }
            lineTeam.setPrefix(parts[0]);
            lineTeam.setSuffix(parts[1]);
            obj.getScore(entry).setScore(score);
            score--;
        }
        for (int extra = lines.size() + 1; extra <= 15; extra++) {
            board.resetScores(invisibleEntry(extra - 1));
            Team leftover = board.getTeam("l" + extra);
            if (leftover != null) {
                leftover.unregister();
            }
        }
        if (plugin.getHost().scoreboard() != null) {
            plugin.getHost().scoreboard().decorate(board, player);
        }
    }

    private String invisibleEntry(int index) {
        ChatColor[] colors = ChatColor.values();
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.append(ChatColor.COLOR_CHAR).append(colors[n % colors.length].getChar());
            n /= colors.length;
        } while (n > 0);
        return sb.toString();
    }

    private String[] split(String text, int limit) {
        if (text.length() <= limit) {
            return new String[] {text, ""};
        }
        int splitIndex = limit;
        if (splitIndex > 0 && text.charAt(splitIndex - 1) == ChatColor.COLOR_CHAR) {
            splitIndex--;
        }
        String prefix = text.substring(0, splitIndex);
        String suffix = ChatColor.getLastColors(prefix) + text.substring(splitIndex);
        if (suffix.length() > limit) {
            suffix = suffix.substring(0, limit);
        }
        return new String[] {prefix, suffix};
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
