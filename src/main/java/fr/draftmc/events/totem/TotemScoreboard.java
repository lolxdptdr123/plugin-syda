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
    /** Plus gros glyphe carré dispo en 1.8 (bloc plein). */
    private static final String SQUARE = "\u2588";
    private static final String HEX = "0123456789abcdef";

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
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = player.getScoreboard();
            if (board != null && board.getObjective("totem") != null) {
                Objective obj = board.getObjective("totem");
                if (obj != null) {
                    obj.unregister();
                }
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
            if (inTournament(player)) {
                continue;
            }
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
     * Intact : █ blanc | XX
     * Cassé : █ gris | pseudo
     */
    private void addBlocRows(List<String> lines, Totem totem) {
        int size = totem.getSize();
        String empty = plugin.getConfig().getString("scoreboard.empty-block", "XX");
        for (int i = size - 1; i >= 0; i--) {
            String breaker = totem.getBreakerAt(i);
            if (breaker == null || breaker.isEmpty()) {
                lines.add(CC.color("&f&l" + SQUARE + " &8| &7" + empty));
            } else {
                lines.add(CC.color("&7&l" + SQUARE + " &8| &f" + clip(breaker, 14)));
            }
        }
    }

    private String clip(String name, int max) {
        if (name == null) {
            return "";
        }
        return name.length() > max ? name.substring(0, max) : name;
    }

    private void update(Player player, String title, List<String> lines) {
        Scoreboard board = player.getScoreboard();
        boolean created = false;
        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()
                || board.getObjective("totem") == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            created = true;
        }
        Objective obj = board.getObjective("totem");
        if (obj == null) {
            Objective sidebar = board.getObjective(DisplaySlot.SIDEBAR);
            if (sidebar != null) {
                sidebar.unregister();
            }
            obj = board.registerNewObjective("totem", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        obj.setDisplayName(truncate(CC.color(title), 32));
        wipeOldLines(board);

        int score = lines.size();
        int index = 0;
        for (String rawLine : lines) {
            String entry = uniqueEntry(index++);
            String[] parts = splitPreservingColor(rawLine, 16);
            String teamName = "l" + index;
            Team lineTeam = board.getTeam(teamName);
            if (lineTeam == null) {
                lineTeam = board.registerNewTeam(teamName);
            }
            for (String old : new ArrayList<String>(lineTeam.getEntries())) {
                if (!old.equals(entry)) {
                    lineTeam.removeEntry(old);
                    board.resetScores(old);
                }
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
            board.resetScores(uniqueEntry(extra - 1));
            Team leftover = board.getTeam("l" + extra);
            if (leftover != null) {
                leftover.unregister();
            }
        }
        decorate(board, player);
        if (created) {
            player.setScoreboard(board);
        }
    }

    private boolean inTournament(Player player) {
        return plugin.getHost().tournament() != null
                && plugin.getHost().tournament().manager().isParticipant(player.getUniqueId());
    }

    private void wipeOldLines(Scoreboard board) {
        ChatColor[] colors = ChatColor.values();
        for (int i = 0; i < colors.length; i++) {
            board.resetScores("" + ChatColor.COLOR_CHAR + colors[i].getChar());
        }
        for (int i = 0; i < 16; i++) {
            board.resetScores(uniqueEntry(i));
            board.resetScores("" + ChatColor.COLOR_CHAR + HEX.charAt(i));
        }
    }

    private String uniqueEntry(int index) {
        return ChatColor.RESET.toString()
                + ChatColor.COLOR_CHAR + HEX.charAt(index % 16)
                + ChatColor.COLOR_CHAR + HEX.charAt((index / 16) % 16)
                + ChatColor.RESET;
    }

    private String[] splitPreservingColor(String text, int limit) {
        if (text.length() <= limit) {
            return new String[] {text, ""};
        }
        int splitIndex = limit;
        if (splitIndex < text.length() && Character.isLowSurrogate(text.charAt(splitIndex))) {
            splitIndex--;
        }
        if (splitIndex < text.length() && Character.isHighSurrogate(text.charAt(splitIndex))) {
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
