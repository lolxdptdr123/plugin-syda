package fr.draftmc.tournament;

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
import java.util.UUID;

public class TournamentScoreboard {
    private static final String SEPARATOR = "&f&m--------------------";
    private final TournamentManager manager;
    private BukkitTask task;

    public TournamentScoreboard(TournamentManager manager) {
        this.manager = manager;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(manager.plugin(), new Runnable() {
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
            if (board != null && board.getObjective("tournament") != null) {
                Objective obj = board.getObjective("tournament");
                if (obj != null) {
                    obj.unregister();
                }
            }
        }
    }

    private void tick() {
        if (!manager.running()) {
            return;
        }
        String title = manager.plugin().getConfig().getString("scoreboard.title", "&c✺ &6Draftmc.fr &c✺");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (manager.signup()) {
                if (manager.teamOf(player.getUniqueId()) == null) {
                    continue;
                }
            } else if (!manager.isParticipant(player.getUniqueId())) {
                continue;
            }
            update(player, title, buildLines(player));
        }
    }

    private List<String> buildLines(Player player) {
        List<String> lines = new ArrayList<String>();
        TournamentDefinition def = manager.runningDef();
        TournamentTeam team = manager.teamOf(player.getUniqueId());
        TournamentMatch match = manager.matchOf(player.getUniqueId());
        lines.add(CC.color(SEPARATOR));
        lines.add(CC.color("&fEvent : &6Tournoi"));
        if (def != null) {
            lines.add(CC.color("&fNom : &e" + def.name()));
        }
        if (manager.signup()) {
            lines.add(CC.color("&fPhase : &eInscriptions"));
            if (def != null) {
                lines.add(CC.color("&fSize : &e" + def.teamSize() + "v" + def.teamSize()));
            }
            lines.add(CC.color("&fÉquipes : &e" + manager.teams().size()));
            if (team != null) {
                lines.add(CC.color("&fÉquipe : &a" + team.name()
                        + " &7(" + team.members().size() + "/" + manager.currentTeamSize() + ")"));
            }
            lines.add(CC.color(SEPARATOR));
            return lines;
        }
        if (team != null) {
            lines.add(CC.color("&fÉquipe : &a" + team.name()));
            if (team.bracket() == TournamentTeam.Bracket.LOSERS) {
                lines.add(CC.color("&fBracket : &cLoser"));
            } else if (team.bracket() == TournamentTeam.Bracket.ELIMINATED) {
                lines.add(CC.color("&fÉtat : &cÉliminé"));
            } else {
                lines.add(CC.color("&fBracket : &aWinner"));
            }
        }
        if (match != null) {
            TournamentTeam opponent = match.opponent(team);
            lines.add(" ");
            if (!match.fighting()) {
                lines.add(CC.color("&fVs : &e" + (opponent == null ? "-" : opponent.name())));
                lines.add(CC.color("&fDébut : &e" + match.countdown() + "s"));
            } else {
                lines.add(CC.color("&fVs : &c" + (opponent == null ? "-" : opponent.name())));
                lines.add(CC.color("&fArène : &e" + match.arena().name()));
                lines.add(CC.color("&a" + match.a().name() + " &f" + alive(match, match.a())
                        + "&7/" + match.a().members().size()));
                lines.add(CC.color("&c" + match.b().name() + " &f" + alive(match, match.b())
                        + "&7/" + match.b().members().size()));
            }
        } else if (team != null && !team.eliminated()) {
            lines.add(CC.color("&7En attente..."));
        }
        lines.add(CC.color(SEPARATOR));
        return lines;
    }

    private int alive(TournamentMatch match, TournamentTeam team) {
        int n = 0;
        for (UUID uuid : team.members()) {
            if (!match.dead().contains(uuid)) {
                n++;
            }
        }
        return n;
    }

    private void update(Player player, String title, List<String> lines) {
        Scoreboard board = player.getScoreboard();
        boolean created = false;
        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()
                || board.getObjective("tournament") == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            created = true;
        }
        Objective obj = board.getObjective("tournament");
        if (obj == null) {
            unregisterSidebar(board);
            obj = board.registerNewObjective("tournament", "dummy");
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
        if (manager.plugin().scoreboard() != null) {
            manager.plugin().scoreboard().decorate(board, player);
        }
        applyRelationNametags(board, player);
        if (created) {
            player.setScoreboard(board);
        }
    }

    private void applyRelationNametags(Scoreboard board, Player viewer) {
        TournamentMatch match = manager.matchOf(viewer.getUniqueId());
        boolean inFight = match != null && match.fighting();
        java.util.Set<String> used = new java.util.HashSet<String>();
        if (inFight) {
            TournamentTeam myTeam = match.teamOf(viewer.getUniqueId());
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!match.hasPlayer(target.getUniqueId())) {
                    continue;
                }
                TournamentTeam theirTeam = match.teamOf(target.getUniqueId());
                boolean ally = myTeam != null && theirTeam == myTeam;
                String teamName = relationTeamName(target, ally);
                used.add(teamName);
                Team dest = board.getTeam(teamName);
                if (dest == null) {
                    dest = board.registerNewTeam(teamName);
                }
                String color = (ally ? ChatColor.GREEN : ChatColor.RED).toString();
                if (!color.equals(dest.getPrefix())) {
                    dest.setPrefix(color);
                }
                String suffix = manager.plugin().scoreboard() == null
                        ? ""
                        : manager.plugin().scoreboard().nametagFactionSuffix(target, color);
                if (!suffix.equals(dest.getSuffix())) {
                    dest.setSuffix(suffix);
                }
                Team current = board.getEntryTeam(target.getName());
                if (current != dest) {
                    if (current != null) {
                        current.removeEntry(target.getName());
                    }
                    dest.addEntry(target.getName());
                }
            }
        }
        for (Team leftover : new ArrayList<Team>(board.getTeams())) {
            String name = leftover.getName();
            if ((name.startsWith("ta") || name.startsWith("te") || name.equals("tally") || name.equals("tenemy"))
                    && !used.contains(name)) {
                leftover.unregister();
            }
        }
    }

    private String relationTeamName(Player player, boolean ally) {
        String hex = Integer.toHexString(player.getUniqueId().hashCode());
        if (hex.length() > 12) {
            hex = hex.substring(0, 12);
        }
        return (ally ? "ta" : "te") + hex;
    }

    private void unregisterSidebar(Scoreboard board) {
        Objective sidebar = board.getObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            sidebar.unregister();
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
