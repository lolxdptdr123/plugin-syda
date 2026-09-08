package fr.draftmc.events.totem;

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
import java.util.Map;

public class TotemScoreboard {
    private static final String SEPARATOR = "&f&m--------------------";
    private static final String ICON_TOP = "\u258C\u258C";
    private static final String ICON_BROKEN = "\u25A1";
    private static final String ICON_INTACT = "\u25A0";

    private final TotemPlugin plugin;
    private BukkitTask task;

    public TotemScoreboard(TotemPlugin plugin) {
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
            if (board != null && board.getObjective("totem") != null) {
                player.setScoreboard(main);
            }
        }
    }

    private void tick() {
        Totem totem = plugin.getTotemManager().getActive();
        if (totem == null) {
            return;
        }
        String title = plugin.getConfig().getString("scoreboard.title",
                plugin.getHost().getConfig().getString("scoreboard.title", "&c✺ &6Draftmc.fr &c✺"));
        List<String> lines = buildLines(totem);
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player, title, lines);
        }
    }

    private List<String> buildLines(Totem totem) {
        if (totem.isGiant() || plugin.getTotemManager().isGiantMode()) {
            return buildGiantLines(totem);
        }
        List<String> lines = new ArrayList<String>();
        String none = plugin.getConfig().getString("scoreboard.none", "-");
        lines.add(CC.color(SEPARATOR));
        lines.add(CC.color("&fEvent : &6Totem"));
        if (totem.getStatus() == TotemStatus.STARTING) {
            int left = plugin.getTotemManager().getCountdownSecondsLeft();
            lines.add(CC.color("&fDemarrage: &e" + Math.max(0, left) + "s"));
            lines.add(" ");
            addBlocHeader(lines, totem);
            addBlocRows(lines, totem);
            lines.add(CC.color(SEPARATOR));
            return lines;
        }
        String casseur = totem.getLastBreakerName() == null ? none : totem.getLastBreakerName();
        String faction = totem.getLastBreakerFaction() == null
                ? (totem.getCapturingFactionId() == null
                    ? none
                    : plugin.getEventFactionHook().getFactionDisplayName(totem.getCapturingFactionId()))
                : totem.getLastBreakerFaction();
        lines.add(CC.color("&fCasseur: &e" + casseur));
        lines.add(CC.color("&fFaction: &b" + faction));
        lines.add(" ");
        addBlocHeader(lines, totem);
        addBlocRows(lines, totem);
        lines.add(CC.color(SEPARATOR));
        return lines;
    }

    private List<String> buildGiantLines(Totem totem) {
        List<String> lines = new ArrayList<String>();
        String none = plugin.getConfig().getString("scoreboard.none", "-");
        lines.add(CC.color(SEPARATOR));
        lines.add(CC.color("&fEvent : &6Totem Geant"));
        if (totem.getStatus() == TotemStatus.STARTING) {
            int left = plugin.getTotemManager().getCountdownSecondsLeft();
            int minutes = plugin.getConfig().getInt("giant.duration-minutes", 30);
            lines.add(CC.color("&fDemarrage: &e" + Math.max(0, left) + "s"));
            lines.add(CC.color("&fDuree: &e" + minutes + " min"));
        } else {
            lines.add(CC.color("&fTemps: &e" + formatTime(plugin.getTotemManager().getDurationSecondsLeft())));
        }
        lines.add(" ");
        lines.add(CC.color("&6Classement"));
        addRankingLines(lines, none);
        lines.add(CC.color(SEPARATOR));
        return lines;
    }

    private void addRankingLines(List<String> lines, String none) {
        List<Map.Entry<String, Integer>> top = plugin.getTotemManager().top(3);
        String[] colors = new String[] {"&e", "&7", "&6"};
        for (int i = 0; i < 3; i++) {
            if (i < top.size()) {
                Map.Entry<String, Integer> entry = top.get(i);
                String name = plugin.getEventFactionHook().getFactionDisplayName(entry.getKey());
                lines.add(CC.color(colors[i] + "#" + (i + 1) + " &f" + name + " &7" + entry.getValue()));
            } else {
                lines.add(CC.color("&8#" + (i + 1) + " &7" + none));
            }
        }
    }

    private String formatTime(int seconds) {
        int s = Math.max(0, seconds);
        int m = s / 60;
        int r = s % 60;
        return m + ":" + (r < 10 ? "0" : "") + r;
    }

    private void addBlocHeader(List<String> lines, Totem totem) {
        lines.add(CC.color("&6Blocs &f(" + totem.getActualSize() + "/" + totem.getSize() + ")"));
    }

    /**
     * Du sommet du totem (Y le plus haut) vers la base. Celui qui casse
     * le bloc du haut reste en haut de la liste, meme s'il casse en dernier.
     */
    private void addBlocRows(List<String> lines, Totem totem) {
        int size = totem.getSize();
        int topIndex = size - 1;
        for (int i = topIndex; i >= 0; i--) {
            String breaker = totem.getBreakerAt(i);
            if (breaker == null || breaker.isEmpty()) {
                lines.add(CC.color("&f" + ICON_INTACT));
            } else if (i == topIndex) {
                lines.add(CC.color("&7" + ICON_TOP + " &8| &f" + breaker));
            } else {
                lines.add(CC.color("&f" + ICON_BROKEN + " &8| &7" + breaker));
            }
        }
    }

    private void update(Player player, String title, List<String> lines) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("totem", "dummy");
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
        decorate(board, player);
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

    private void decorate(Scoreboard board, Player player) {
        if (plugin.getHost().scoreboard() != null) {
            plugin.getHost().scoreboard().decorate(board, player);
        }
    }
}
