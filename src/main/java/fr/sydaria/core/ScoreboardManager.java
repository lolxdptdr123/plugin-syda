package fr.sydaria.core;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Locale;

public class ScoreboardManager {
    private static final int MAX_LINES = 15;
    private final Sydaria plugin;
    private int task;

    public ScoreboardManager(Sydaria plugin) {
        this.plugin = plugin;
        int refresh = plugin.getConfig().getInt("scoreboard.refresh-ticks", 20);
        this.task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    update(player);
                }
            }
        }, 20L, refresh);
    }

    public void disable() {
        Bukkit.getScheduler().cancelTask(task);
    }

    public void update(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        Scoreboard board = player.getScoreboard();
        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }
        Objective obj = board.getObjective("sydaria");
        if (obj == null) {
            obj = board.registerNewObjective("sydaria", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        obj.setDisplayName(CC.color(plugin.getConfig().getString("scoreboard.title", "&c✺ &6Sydaria.fr &c✺")));

        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
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

        if (plugin.getConfig().getBoolean("core.nametag-health", true)) {
            Objective health = board.getObjective("sydhp");
            if (health == null) {
                health = board.registerNewObjective("sydhp", "health");
                health.setDisplaySlot(DisplaySlot.BELOW_NAME);
            }
            health.setDisplayName(CC.color(plugin.getConfig().getString("core.nametag-health-suffix", "&c❤")));
        }

        applyGradeTeams(board);
    }

    /**
     * Colore le pseudo de CHAQUE joueur en ligne dans le tab-list et au-dessus de sa
     * tête, du point de vue de "player" (le joueur dont on vient de rafraîchir le
     * scoreboard, donc le "spectateur" ici).
     *
     * Technique standard 1.8 : un joueur qui appartient à une Team sur le scoreboard
     * du spectateur voit son nom entouré du préfixe/suffixe de cette Team, à la fois
     * dans le tab-list et au-dessus de sa tête. On regroupe donc les joueurs par
     * grade (même couleur = même Team) et on assigne chaque joueur en ligne à la
     * Team qui correspond à SON grade — pas celui du spectateur.
     *
     * On réutilise plugin.grades().chatPrefix() : c'est exactement la même chaîne
     * "&dStar " déjà utilisée dans le chat, donc le rendu reste cohérent partout.
     */
    private void applyGradeTeams(Scoreboard board) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            String group = plugin.grades().group(target);
            boolean graded = !plugin.grades().color(target).isEmpty();
            String teamName = "g" + (graded ? safeTeamKey(group) : "none");
            String prefix = graded ? plugin.grades().chatPrefix(target) : "";
            if (prefix.length() > 16) {
                prefix = prefix.substring(0, 16);
            }
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
                team.setPrefix(prefix);
                team.setSuffix("");
            } else if (!prefix.equals(team.getPrefix())) {
                team.setPrefix(prefix);
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
    }

    /** Nom d'équipe scoreboard valide en 1.8 (16 caractères max, alphanumérique). */
    private String safeTeamKey(String group) {
        String key = group.replaceAll("[^a-zA-Z0-9]", "");
        if (key.isEmpty()) {
            key = "x";
        }
        if (key.length() > 15) {
            key = key.substring(0, 15);
        }
        return key;
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
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                out = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, out);
            } catch (Throwable ignored) {
            }
        }
        return CC.color(out);
    }

    private String factionValue(Player player) {
        String id = plugin.factions().factionOf(player);
        if (id == null || id.isEmpty()) {
            return "X";
        }
        return plugin.factions().displayName(id);
    }

    private String grade(Player player) {
        // Le grade (VIP/MVP/Staff...) est déterminé par GradeManager à partir du groupe
        // de permission (LuckPerms/PEX via Vault) : c'est la seule source de vérité,
        // partagée avec le préfixe affiché dans le chat. Voir fr.sydaria.grades.GradeManager.
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
        String suffix = ChatColor.getLastColors(prefix) + text.substring(cut);
        if (suffix.length() > 16) {
            suffix = suffix.substring(0, 16);
        }
        team.setPrefix(prefix);
        team.setSuffix(suffix);
    }

    private String uniqueEntry(int i) {
        return ChatColor.COLOR_CHAR + Integer.toHexString(i) + ChatColor.RESET;
    }
}
