package fr.draftmc.core;

import fr.draftmc.Draftmc;
import fr.draftmc.events.EventHub;
import fr.draftmc.util.CC;
import fr.draftmc.util.NMS;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ScoreboardManager {
    private static final int MAX_LINES = 15;
    private final Draftmc plugin;
    private int task;
    private int healthTask;
    private final Map<UUID, String> lastTabName = new HashMap<UUID, String>();

    public ScoreboardManager(Draftmc plugin) {
        this.plugin = plugin;
        int refresh = plugin.getConfig().getInt("scoreboard.refresh-ticks", 20);
        this.task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    update(player);
                }
                syncTabNames();
            }
        }, 20L, refresh);
        int healthRefresh = Math.max(1, plugin.getConfig().getInt("core.nametag-health-refresh-ticks", 1));
        this.healthTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                refreshNametagHealth();
            }
        }, 1L, healthRefresh);
    }

    public void disable() {
        Bukkit.getScheduler().cancelTask(task);
        Bukkit.getScheduler().cancelTask(healthTask);
    }

    private boolean isEventScoreboard(Scoreboard board) {
        return board.getObjective("domination") != null
                || board.getObjective("masterkill") != null
                || board.getObjective("br_sidebar") != null
                || board.getObjective("totem") != null
                || board.getObjective("koth") != null
                || board.getObjective("teamfight") != null
                || board.getObjective("tournament") != null;
    }

    private boolean isTournamentPlayer(Player player) {
        return plugin.tournament() != null && plugin.tournament().manager() != null
                && plugin.tournament().manager().isParticipant(player.getUniqueId());
    }

    public void update(Player player) {
        sendTabList(player);
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        Scoreboard current = player.getScoreboard();
        if (isTournamentPlayer(player) || (current != null && isEventScoreboard(current))) {
            return;
        }
        Scoreboard board = current;
        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }
        Objective obj = board.getObjective("draftmc");
        if (obj == null) {
            obj = board.registerNewObjective("draftmc", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        obj.setDisplayName(CC.color(plugin.getConfig().getString("scoreboard.title", "&c✺ &6Draftmc.fr &c✺")));

        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        lines = mergeConquest(player, lines);
        if (lines.size() > MAX_LINES) {
            lines = lines.subList(0, MAX_LINES);
        }
        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String line = apply(player, lines.get(i));
            String teamName = "l" + i;
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            String entry = uniqueEntry(i);
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            split(team, line);
            obj.getScore(entry).setScore(score);
            score--;
        }

        for (int i = lines.size(); i < MAX_LINES; i++) {
            String entry = uniqueEntry(i);
            board.resetScores(entry);
            Team leftover = board.getTeam("l" + i);
            if (leftover != null) {
                leftover.unregister();
            }
        }

        applyNametagHealth(board);
        applyGradeTeams(board, player);
    }

    private List<String> mergeConquest(Player player, List<String> base) {
        if (plugin.events() == null || plugin.events().conquest() == null) {
            return base;
        }
        fr.draftmc.events.conquest.managers.ScoreboardManager conquestBoard = plugin.events().conquest().getScoreboardManager();
        if (conquestBoard == null || !conquestBoard.active()) {
            return base;
        }
        List<String> extra = conquestBoard.linesFor(player);
        if (extra == null || extra.isEmpty()) {
            return base;
        }
        List<String> out = new ArrayList<String>();
        int cut = -1;
        for (int i = 0; i < base.size(); i++) {
            String plain = ChatColor.stripColor(CC.color(base.get(i))).trim();
            if (plain.equalsIgnoreCase("Infos")) {
                cut = i;
                break;
            }
        }
        if (cut < 0) {
            out.addAll(base);
            out.addAll(extra);
        } else {
            out.addAll(base.subList(0, cut));
            out.addAll(extra);
            String last = base.get(base.size() - 1);
            if (last.contains("&m") && !out.get(out.size() - 1).contains("&m")) {
                out.add(last);
            }
        }
        return out;
    }

    /**
     * Prefixe de grade au-dessus de la tete / tab, a appliquer aussi sur les
     * scoreboards d'events (sinon un newScoreboard les efface).
     */
    public void decorate(Scoreboard board) {
        decorate(board, null);
    }

    public void decorate(Scoreboard board, Player viewer) {
        if (board == null) {
            return;
        }
        applyGradeTeams(board, viewer);
        applyNametagHealth(board);
    }

    private void applyNametagHealth(Scoreboard board) {
        if (board == null || !plugin.getConfig().getBoolean("core.nametag-health", true)) {
            return;
        }
        Objective health = board.getObjective("dmchp");
        if (health != null && !"dummy".equalsIgnoreCase(health.getCriteria())) {
            health.unregister();
            health = null;
        }
        if (health == null) {
            health = board.registerNewObjective("dmchp", "dummy");
            health.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        health.setDisplayName(CC.color(plugin.getConfig().getString("core.nametag-health-suffix", "&c❤")));
        for (Player online : Bukkit.getOnlinePlayers()) {
            int hp;
            if (online.isDead()) {
                hp = 0;
            } else {
                hp = (int) Math.round(online.getHealth());
                if (hp < 1) {
                    hp = (int) Math.round(online.getMaxHealth());
                }
            }
            org.bukkit.scoreboard.Score score = health.getScore(online.getName());
            if (score.getScore() != hp) {
                score.setScore(hp);
            }
        }
    }

    private void refreshNametagHealth() {
        if (!plugin.getConfig().getBoolean("core.nametag-health", true)) {
            return;
        }
        Set<Scoreboard> seen = new HashSet<Scoreboard>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = player.getScoreboard();
            if (board == null || !seen.add(board)) {
                continue;
            }
            applyNametagHealth(board);
        }
    }

    /**
     * Colore le pseudo de CHAQUE joueur en ligne dans le tab-list et au-dessus de sa
     * tête, du point de vue de "player" (le joueur dont on vient de rafraîchir le
     * scoreboard, donc le "spectateur" ici).
     *
     * Technique standard 1.8 : un joueur qui appartient à une Team sur le scoreboard
     * du spectateur voit son nom entouré du préfixe/suffixe de cette Team, à la fois
     * dans le tab-list et au-dessus de sa tête.
     * Prefixe = grade. Suffixe = faction au-dessus de la tete.
     * Le tab utilise un nom separe (sans faction).
     */
    private void applyGradeTeams(Scoreboard board, Player viewer) {
        Set<String> used = new HashSet<String>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            String teamName = nametagTeamName(target);
            used.add(teamName);
            boolean graded = plugin.grades() != null && !plugin.grades().color(target).isEmpty();
            String prefix = graded ? plugin.grades().chatPrefix(target) : "";
            if (prefix.length() > 16) {
                prefix = prefix.substring(0, 16);
            }
            String suffix = nametagFactionSuffix(target, nametagRelationColor(viewer, target));
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            if (!prefix.equals(team.getPrefix())) {
                team.setPrefix(prefix);
            }
            if (!suffix.equals(team.getSuffix())) {
                team.setSuffix(suffix);
            }
            String entry = target.getName();
            Team current = board.getEntryTeam(entry);
            if (current != team) {
                if (current != null) {
                    current.removeEntry(entry);
                }
                team.addEntry(entry);
            }
        }
        for (Team leftover : new ArrayList<Team>(board.getTeams())) {
            String name = leftover.getName();
            if (name.startsWith("nt") && !used.contains(name)) {
                leftover.unregister();
            }
        }
    }

    private void syncTabNames() {
        if (!plugin.getConfig().getBoolean("scoreboard.tab-hide-faction", true)) {
            return;
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            String name = tabNameOf(target);
            if (name.equals(lastTabName.get(target.getUniqueId()))) {
                continue;
            }
            lastTabName.put(target.getUniqueId(), name);
            NMS.setTabDisplayName(target, name);
        }
        lastTabName.keySet().retainAll(onlineIds());
    }

    private Set<UUID> onlineIds() {
        Set<UUID> ids = new HashSet<UUID>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ids.add(player.getUniqueId());
        }
        return ids;
    }

    private String tabNameOf(Player player) {
        String prefix = "";
        if (plugin.grades() != null && !plugin.grades().color(player).isEmpty()) {
            prefix = plugin.grades().chatPrefix(player);
        }
        return prefix + player.getName();
    }

    private String nametagTeamName(Player player) {
        int weight = 99;
        if (plugin.grades() != null) {
            weight = Math.min(99, Math.max(0, plugin.grades().tabWeight(player)));
        }
        String hex = Integer.toHexString(player.getUniqueId().hashCode());
        if (hex.length() > 8) {
            hex = hex.substring(0, 8);
        }
        return "nt" + (weight < 10 ? "0" + weight : String.valueOf(weight)) + hex;
    }

    public String nametagFactionSuffix(Player player) {
        return nametagFactionSuffix(player, nametagRelationColor(null, player));
    }

    public String nametagFactionSuffix(Player player, String relationColor) {
        if (player == null || plugin.factions() == null
                || !plugin.getConfig().getBoolean("scoreboard.nametag-faction", true)) {
            return "";
        }
        String fac = plugin.factions().factionOf(player);
        if (fac == null || fac.isEmpty()) {
            return "";
        }
        String color = relationColor == null || relationColor.isEmpty() ? ChatColor.RED.toString() : relationColor;
        String out = color + CC.color(plugin.getConfig().getString("scoreboard.nametag-faction-suffix", " {faction}")
                .replace("{faction}", plugin.factions().displayName(fac)));
        return out.length() > 16 ? out.substring(0, 16) : out;
    }

    private String nametagRelationColor(Player viewer, Player target) {
        if (target == null) {
            return ChatColor.RED.toString();
        }
        if (viewer != null && viewer.getUniqueId().equals(target.getUniqueId())) {
            return ChatColor.GREEN.toString();
        }
        if (viewer == null || plugin.factions() == null) {
            return ChatColor.RED.toString();
        }
        String from = plugin.factions().factionOf(viewer);
        String to = plugin.factions().factionOf(target);
        if (from != null && !from.isEmpty() && from.equalsIgnoreCase(to)) {
            return ChatColor.GREEN.toString();
        }
        return ChatColor.RED.toString();
    }

    private String apply(Player player, String line) {
        String out = line
                .replace("%player%", player.getName())
                .replace("%kills%", String.valueOf(plugin.data().getInt(player.getUniqueId(), "players_killed")))
                .replace("%deaths%", String.valueOf(plugin.data().getInt(player.getUniqueId(), "deaths")))
                .replace("%tokens%", compact(plugin.tokens().get(player.getUniqueId())))
                .replace("%faction%", factionValue(player))
                .replace("%grade%", grade(player))
                .replace("%power%", powerValue(player))
                .replace("%flyclaim%", flyClaim(player))
                .replace("%votes%", voteValue())
                .replace("%money%", compact(plugin.economy().getMoney(player)))
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%combat%", combatValue(player))
                .replace("%ping%", pingValue(player))
                .replace("%nextevent%", nextEventName())
                .replace("%nextevent_time%", nextEventTime());
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                out = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, out);
            } catch (Throwable ignored) {
            }
        }
        return CC.color(out);
    }

    private String combatValue(Player player) {
        if (plugin.combat() == null || !plugin.combat().isTagged(player)) {
            return "Non";
        }
        return plugin.combat().remainingSeconds(player) + "s";
    }

    private String pingValue(Player player) {
        int ping = PingCommand.pingOf(player);
        if (ping < 0) {
            return "&7-";
        }
        String color;
        if (ping < 50) {
            color = "&a";
        } else if (ping < 100) {
            color = "&e";
        } else if (ping < 200) {
            color = "&6";
        } else {
            color = "&c";
        }
        return color + ping + "ms";
    }

    private void sendTabList(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.tab-header-footer", true)) {
            return;
        }
        NMS.tabHeaderFooter(player,
                joinTab(player, "scoreboard.tab-header", "&6&lDraftmc"),
                joinTab(player, "scoreboard.tab-footer", "&eProchain événement\n&6%nextevent% &7- &f%nextevent_time%"));
    }

    private String joinTab(Player player, String path, String fallback) {
        List<String> lines = plugin.getConfig().getStringList(path);
        String raw;
        if (lines == null || lines.isEmpty()) {
            raw = plugin.getConfig().getString(path, fallback);
            if (raw == null || raw.isEmpty()) {
                raw = fallback;
            }
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append(lines.get(i));
            }
            raw = sb.toString();
        }
        return apply(player, raw);
    }

    private String nextEventName() {
        if (plugin.events() == null) {
            return "Aucun";
        }
        EventHub.Upcoming next = plugin.events().nextUpcoming();
        return next == null ? "Aucun" : next.name;
    }

    private String nextEventTime() {
        if (plugin.events() == null) {
            return "-";
        }
        EventHub.Upcoming next = plugin.events().nextUpcoming();
        return next == null ? "-" : EventHub.formatUntil(next.atMillis);
    }

    private String factionValue(Player player) {
        String id = plugin.factions().factionOf(player);
        if (id == null || id.isEmpty()) {
            return CC.color("&aX");
        }
        return CC.color("&a" + plugin.factions().displayName(id));
    }

    private String grade(Player player) {
        // Le grade (VIP/MVP/Staff...) est déterminé par GradeManager à partir du groupe
        // de permission (LuckPerms/PEX via Vault) : c'est la seule source de vérité,
        // partagée avec le préfixe affiché dans le chat. Voir fr.draftmc.grades.GradeManager.
        return plugin.grades().displayName(player);
    }

    private String powerValue(Player player) {
        int current = (int) Math.round(plugin.factions().currentPower(player.getUniqueId()));
        int max = (int) Math.round(plugin.getConfig().getDouble("factions.power.max", 10));
        return current + " / " + max;
    }

    private String flyClaim(Player player) {
        long until = plugin.data().getLong(player.getUniqueId(), "fly_claim_until");
        long remaining = until - System.currentTimeMillis();
        if (remaining > 0) {
            return formatDuration(remaining);
        }
        String fac = plugin.factions().factionOf(player);
        if (!fac.isEmpty() && plugin.factions().hasFly(fac)) {
            return "Actif";
        }
        return "0s";
    }

    private String voteValue() {
        if (plugin.voteParty() == null) {
            return "0/0";
        }
            return plugin.voteParty().current() + " / " + plugin.voteParty().goal();
    }

    static String compact(double amount) {
        double abs = Math.abs(amount);
        String suffix;
        double scaled;
        if (abs >= 1_000_000_000d) {
            scaled = amount / 1_000_000_000d;
            suffix = "B";
        } else if (abs >= 1_000_000d) {
            scaled = amount / 1_000_000d;
            suffix = "M";
        } else if (abs >= 1_000d) {
            scaled = amount / 1_000d;
            suffix = "k";
        } else {
            if (Math.abs(amount - Math.round(amount)) < 0.05) {
                return String.valueOf(Math.round(amount));
            }
            return String.format(Locale.US, "%.1f", amount);
        }
        String body = String.format(Locale.US, "%.1f", scaled);
        if (body.endsWith(".0")) {
            body = body.substring(0, body.length() - 2);
        }
        return body + suffix;
    }

    private String formatDuration(long ms) {
        long sec = Math.max(0, ms / 1000L);
        long h = sec / 3600L;
        long m = (sec % 3600L) / 60L;
        long s = sec % 60L;
        if (h > 0) {
            return h + "h " + m + "m " + s + "s";
        }
        if (m > 0) {
            return m + "m " + s + "s";
        }
        return s + "s";
    }

    private void split(Team team, String text) {
        if (text == null) {
            text = "";
        }
        if (text.length() <= 16) {
            team.setPrefix(text);
            team.setSuffix("");
            return;
        }
        int cut = 16;
        if (text.charAt(15) == ChatColor.COLOR_CHAR) {
            cut = 15;
        }
        String prefix = text.substring(0, cut);
        String rest = text.substring(cut);
        String suffix;
        if (rest.length() > 0 && rest.charAt(0) == ChatColor.COLOR_CHAR) {
            suffix = rest;
        } else {
            suffix = ChatColor.getLastColors(prefix) + rest;
        }
        if (suffix.length() > 16) {
            suffix = suffix.charAt(15) == ChatColor.COLOR_CHAR
                    ? suffix.substring(0, 15)
                    : suffix.substring(0, 16);
        }
        team.setPrefix(prefix);
        team.setSuffix(suffix);
    }

    private String uniqueEntry(int i) {
        return ChatColor.COLOR_CHAR + Integer.toHexString(i) + ChatColor.RESET;
    }
}
