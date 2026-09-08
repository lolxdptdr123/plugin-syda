package fr.draftmc.events.teamfight;

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

public class TeamFightScoreboard {
    private static final String SEPARATOR = "&f&m--------------------";
    private final TeamFightPlugin plugin;
    private BukkitTask task;

    public TeamFightScoreboard(TeamFightPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 0L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = player.getScoreboard();
            if (board != null && board.getObjective("teamfight") != null) {
                player.setScoreboard(main);
            }
        }
    }

    private void tick() {
        if (!plugin.getManager().isBusy()) {
            return;
        }
        String title = plugin.getConfig().getString("scoreboard.title",
                plugin.getHost().getConfig().getString("scoreboard.title", "&c✺ &6Draftmc.fr &c✺"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player, title, buildLines(player));
        }
    }

    private List<String> buildLines(Player player) {
        List<String> lines = new ArrayList<String>();
        TeamFightManager mgr = plugin.getManager();
        TfMatch match = mgr.getCurrentMatch();
        lines.add(CC.color(SEPARATOR));
        lines.add(CC.color("&fEvent : &6TeamFight 8v8"));
        if (match != null) {
            lines.add(CC.color("&fManche : &e" + match.getRound()));
            lines.add(" ");
            lines.add(CC.color("&b" + match.getTeamA().getName()
                    + " &f" + mgr.aliveCount(match.getTeamA()) + "&7/" + match.getTeamA().getMembers().size()));
            lines.add(CC.color("&c" + match.getTeamB().getName()
                    + " &f" + mgr.aliveCount(match.getTeamB()) + "&7/" + match.getTeamB().getMembers().size()));
        } else {
            lines.add(CC.color("&7Preparation..."));
        }
        FightStats stats = mgr.statsOf(player.getUniqueId());
        if (mgr.isFighter(player.getUniqueId()) || mgr.isDeadThisFight(player.getUniqueId())) {
            lines.add("  ");
            lines.add(CC.color("&fHits : &e" + stats.getHits()));
            lines.add(CC.color("&fPotions : &b" + stats.getPotions()));
        }
        lines.add(CC.color(SEPARATOR));
        return lines;
    }

    private void update(Player player, String title, List<String> lines) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("teamfight", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(truncate(CC.color(title), 32));
        int score = lines.size();
        int index = 0;
        for (String rawLine : lines) {
            String entry = invisibleEntry(index++);
            String[] parts = splitPreservingColor(rawLine, 16);
            Team lineTeam = board.registerNewTeam("l" + index);
            lineTeam.addEntry(entry);
            lineTeam.setPrefix(parts[0]);
            lineTeam.setSuffix(parts[1]);
            obj.getScore(entry).setScore(score);
            score--;
        }
        if (plugin.getHost().scoreboard() != null) {
            plugin.getHost().scoreboard().decorate(board, player);
        }
        player.setScoreboard(board);
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

    private String[] splitPreservingColor(String text, int limit) {
        if (text.length() <= limit) {
            return new String[] {text, ""};
        }
        int splitIndex = limit;
        if (splitIndex < text.length() && Character.isLowSurrogate(text.charAt(splitIndex))) {
            splitIndex--;
        }
        if (splitIndex > 0 && text.charAt(splitIndex - 1) == ChatColor.COLOR_CHAR) {
            splitIndex--;
        }
        String prefix = text.substring(0, splitIndex);
        String rest = text.substring(splitIndex);
        String suffix = ChatColor.getLastColors(prefix) + rest;
        if (suffix.length() > limit) {
            suffix = suffix.substring(0, limit);
        }
        return new String[] {prefix, suffix};
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
