package fr.draftmc.events.domination.managers;

import fr.draftmc.events.domination.DominationPlugin;
import fr.draftmc.events.domination.model.Zone;
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
import java.util.UUID;

/**
 * Sidebar live : un carre colore par zone (vert si un membre de TA faction
 * y est present et y marque des points, gris sinon) suivi de tes propres
 * points sur cette zone, puis le top 3 des factions ayant des points
 * (jamais les factions a 0), et ton total general. Rafraichi chaque
 * seconde.
 *
 * NOTE 1.8 : le protocole du jeu limite chaque ligne de scoreboard a un
 * "faux joueur" (l'entry) dont le nom brut ne peut depasser 16 caracteres
 * visibles - c'est cette limite qui coupait les points a droite. On la
 * contourne avec la technique standard 1.8 (equipes + prefix/suffix) :
 * chaque ligne devient une Team dont l'entry est une chaine de codes
 * couleur invisible (0 caractere visible), et le texte reel de la ligne
 * est reparti entre team.setPrefix() et team.setSuffix() (16 caracteres
 * visibles chacun, plafond impose par le protocole 1.8 pour ces deux
 * champs). Cela porte la largeur utile de 16 a 32 caracteres visibles par
 * ligne sans aucune dependance NMS.
 */
public class ScoreboardManager {

    private final DominationPlugin plugin;
    private BukkitTask task;

    public ScoreboardManager(DominationPlugin plugin) {
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
        String title = plugin.getConfig().getString("scoreboard.title", "&6&lDOMINATION");
        int pointsToWin = plugin.getConfig().getInt("general.points-to-win", 2000);

        // Classement global, calcule une seule fois par tick (pas par
        // joueur) : seules les factions ayant au moins 1 point apparaissent.
        List<Map.Entry<String, Integer>> ranking = new ArrayList<>(plugin.getDominationManager().getAllTotals().entrySet());
        ranking.removeIf(entry -> entry.getValue() <= 0);
        ranking.sort((a, b) -> b.getValue() - a.getValue());

        for (Player player : Bukkit.getOnlinePlayers()) {
            String factionId = plugin.getEventFactionHook().getFactionId(player);
            int total = plugin.getDominationManager().getFactionTotal(factionId);

            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.GRAY + "Objectif: " + pointsToWin + "pts");
            lines.add(" ");

            for (Zone zone : plugin.getZoneManager().getZones().values()) {
                boolean myFactionScoringHere = isFactionPresent(zone, factionId);
                ChatColor squareColor = myFactionScoringHere ? ChatColor.GREEN : ChatColor.GRAY;
                lines.add(squareColor + "\u25A0" + ChatColor.RESET + " " + zone.getDisplayName()
                        + ChatColor.GRAY + " " + zone.getPoints(factionId));
            }

            lines.add("  ");
            if (!ranking.isEmpty()) {
                lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Top factions:");
                int rank = 1;
                for (Map.Entry<String, Integer> entry : ranking) {
                    if (rank > 3) break;
                    String name = plugin.getEventFactionHook().getFactionDisplayName(entry.getKey());
                    lines.add(ChatColor.YELLOW + "" + rank + ". " + ChatColor.WHITE + name
                            + ChatColor.GRAY + " " + entry.getValue());
                    rank++;
                }
                lines.add("   ");
            }

            lines.add(ChatColor.GOLD + "Total: " + total + "/" + pointsToWin);

            update(player, title, lines);
        }
    }

    /** true si au moins un membre de cette faction est physiquement present dans la zone (donc y marque des points). Renvoie false si factionId est null (joueur sans faction). */
    private boolean isFactionPresent(Zone zone, String factionId) {
        if (factionId == null) return false;
        for (UUID uuid : zone.getPlayersInside()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && factionId.equals(plugin.getEventFactionHook().getFactionId(p))) {
                return true;
            }
        }
        return false;
    }

    /** Nombre de caracteres visibles maximum autorises par le protocole 1.8 pour un prefix/suffix d'equipe. */
    private static final int TEAM_TEXT_LIMIT = 16;

    /** Jeu de codes utilises pour fabriquer des entries invisibles et uniques (voir {@link #invisibleEntry(int)}). */
    private static final ChatColor[] CODE_ALPHABET = ChatColor.values();

    private void update(Player player, String title, List<String> lines) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("domination", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(truncate(ChatColor.translateAlternateColorCodes('&', title), 32));

        int score = lines.size();
        int index = 0;
        for (String rawLine : lines) {
            String[] parts = splitForTeam(rawLine);
            String entry = invisibleEntry(index);

            // Chaque ligne est portee par sa propre Team : c'est prefix+suffix
            // qui s'affiche, l'entry (invisible) ne sert qu'a ordonner la ligne.
            Team team = board.registerNewTeam("dom_" + index);
            team.addEntry(entry);
            team.setPrefix(parts[0]);
            team.setSuffix(parts[1]);

            obj.getScore(entry).setScore(score);
            score--;
            index++;
        }
        decorate(board, player);
        player.setScoreboard(board);
    }

    /**
     * Repartit une ligne en {prefix, suffix} de 16 caracteres visibles
     * maximum chacun. La coupure evite de trancher au milieu d'un code
     * couleur ("&sect;x"), et la couleur/format actifs en fin de prefix
     * sont reportes en debut de suffix pour que le style ne se "coupe" pas
     * visuellement au niveau de la jointure.
     */
    private String[] splitForTeam(String line) {
        String prefix = safeCut(line, TEAM_TEXT_LIMIT);
        String remainder = line.substring(prefix.length());
        if (!remainder.isEmpty()) {
            remainder = ChatColor.getLastColors(prefix) + remainder;
        }
        String suffix = safeCut(remainder, TEAM_TEXT_LIMIT);
        return new String[]{prefix, suffix};
    }

    /** Coupe {@code s} a {@code max} caracteres sans jamais separer un "&sect;" de son code de couleur. */
    private String safeCut(String s, int max) {
        if (s.length() <= max) return s;
        int cut = max;
        if (s.charAt(cut - 1) == ChatColor.COLOR_CHAR) {
            cut--;
        }
        return s.substring(0, cut);
    }

    /**
     * Genere une entry (nom de "faux joueur") unique et strictement
     * invisible pour la ligne d'indice {@code index} : une suite de codes
     * couleur (2 caracteres chacun, aucun glyphe rendu) encodee en base
     * {@code CODE_ALPHABET.length}. Avec 22 codes disponibles et un budget
     * de 16 caracteres (8 codes), cela couvre 22^8 lignes possibles - trivialement
     * suffisant pour un scoreboard qui en affiche une quinzaine.
     */
    private String invisibleEntry(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        do {
            sb.append(CODE_ALPHABET[n % CODE_ALPHABET.length]);
            n /= CODE_ALPHABET.length;
        } while (n > 0);
        return sb.toString();
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
