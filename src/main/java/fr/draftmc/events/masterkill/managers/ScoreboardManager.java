package fr.draftmc.events.masterkill.managers;

import fr.draftmc.events.masterkill.MasterKillPlugin;
import fr.draftmc.events.masterkill.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Sidebar live :
 *  Event: MasterKill
 *  Temps restant: MM:SS
 *  Vos kills: N (kills INDIVIDUELS du joueur, pas de son equipe)
 *  Classement: toujours 3 lignes - une equipe qui n'a pas encore marque
 *  affiche une croix rouge (X) a la place, plutot que d'etre listee a 0 ou masquee.
 *
 * IMPORTANT sur la longueur des lignes : le protocole 1.8 limite une ligne
 * de scoreboard "classique" (Objective+Score) a 16 caracteres visibles.
 * Pour depasser cette limite, chaque ligne est en realite un "Team"
 * scoreboard avec un prefixe (jusqu'a 16 caracteres) + un suffixe (jusqu'a
 * 16 caracteres de plus) autour d'une entree invisible unique - ce qui
 * porte la capacite reelle a ~32 caracteres visibles par ligne.
 */
public class ScoreboardManager {

    private final MasterKillPlugin plugin;
    private BukkitTask task;

    public ScoreboardManager(MasterKillPlugin plugin) {
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
        String title = plugin.getConfig().getString("scoreboard.title", "&c&lMASTERKILL");

        // Seules les equipes ayant deja marque au moins 1 kill participent
        // au classement ; les 3 emplacements du top sont TOUJOURS affiches,
        // avec une croix rouge (X) pour un emplacement pas encore atteint par personne.
        List<Team> scoringTeams = new ArrayList<>(plugin.getTeamManager().getTeams());
        scoringTeams.removeIf(t -> plugin.getKillManager().getKills(t.getName()) <= 0);
        scoringTeams.sort((a, b) -> plugin.getKillManager().getKills(b.getName()) - plugin.getKillManager().getKills(a.getName()));

        List<String> classement = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (i < scoringTeams.size()) {
                Team team = scoringTeams.get(i);
                classement.add(team.getDisplayName() + ChatColor.GRAY + " (" + plugin.getKillManager().getKills(team.getName()) + ")");
            } else {
                // "X" ASCII simple plutot qu'un caractere Unicode (❌) : ce
                // dernier ne s'affichait pas de facon fiable selon les
                // clients/polices, alors qu'un caractere ASCII classique
                // fonctionne garanti a 100% partout.
                classement.add(ChatColor.RED + "X");
            }
        }

        long remaining = plugin.getMasterKillManager().getRemainingSeconds();
        String timeFormatted = formatTime(remaining);

        for (Player player : Bukkit.getOnlinePlayers()) {
            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.GRAY + "Event: " + ChatColor.WHITE + "MasterKill");
            lines.add(" ");
            lines.add(ChatColor.GRAY + "Temps restant: " + ChatColor.WHITE + timeFormatted);
            lines.add(ChatColor.GRAY + "Vos kills: " + ChatColor.WHITE + plugin.getKillManager().getPersonalKills(player.getUniqueId()));
            lines.add("  ");
            lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Classement:");
            lines.addAll(classement);

            update(player, title, lines);
        }
    }

    private String formatTime(long seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private void update(Player player, String title, List<String> lines) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("masterkill", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(truncate(ChatColor.translateAlternateColorCodes('&', title), 32));

        int score = lines.size();
        int index = 0;
        for (String rawLine : lines) {
            String entry = invisibleEntry(index++);
            String[] parts = splitPreservingColor(rawLine, 16);

            org.bukkit.scoreboard.Team lineTeam = board.registerNewTeam("l" + index);
            lineTeam.addEntry(entry);
            lineTeam.setPrefix(parts[0]);
            lineTeam.setSuffix(parts[1]);

            obj.getScore(entry).setScore(score);
            score--;
        }
        decorate(board, player);
        player.setScoreboard(board);
    }

    /**
     * Genere une entree unique et INVISIBLE (uniquement des codes couleur,
     * aucun caractere visible) a partir d'un index de ligne - garantit
     * l'unicite sans jamais empieter sur le texte affiche.
     */
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

    /**
     * Coupe une ligne (potentiellement formatee avec des codes couleur) en
     * un prefixe et un suffixe d'au plus "limit" caracteres chacun, en
     * reportant le dernier code couleur actif du prefixe au debut du
     * suffixe (via ChatColor.getLastColors, utilitaire Bukkit standard),
     * pour que la couleur/le formatage continuent correctement au lieu de
     * se couper au milieu ou de revenir au blanc par defaut.
     */
    private String[] splitPreservingColor(String text, int limit) {
        if (text.length() <= limit) {
            return new String[]{text, ""};
        }

        int splitIndex = limit;
        if (text.charAt(splitIndex - 1) == ChatColor.COLOR_CHAR) {
            splitIndex--;
        }

        String prefix = text.substring(0, splitIndex);
        String rest = text.substring(splitIndex);
        String suffix = ChatColor.getLastColors(prefix) + rest;
        if (suffix.length() > limit) {
            suffix = suffix.substring(0, limit);
        }
        return new String[]{prefix, suffix};
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
