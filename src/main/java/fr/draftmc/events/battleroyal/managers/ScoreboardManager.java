package fr.draftmc.events.battleroyal.managers;

import fr.draftmc.events.battleroyal.BattleRoyal;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gere le scoreboard (sidebar) de chaque joueur pendant la partie, et
 * restaure ensuite EXACTEMENT le scoreboard qu'avait le joueur avant
 * (par exemple celui d'un plugin externe type SimpleScore), plutot que
 * de le remplacer par le scoreboard principal vide du serveur.
 *
 * NOTE IMPORTANTE (limite native du protocole 1.8) :
 * chaque ligne de scoreboard est en realite un "faux joueur" limite a
 * 16 caracteres visibles. On tronque donc les lignes et on desambiguise
 * les doublons avec des codes couleur invisibles (astuce classique 1.8).
 */
public class ScoreboardManager {

    private final BattleRoyal plugin;
    private final Map<UUID, Scoreboard> previousBoards = new HashMap<>();

    public ScoreboardManager(BattleRoyal plugin) {
        this.plugin = plugin;
    }

    public void update(Player player, String title, List<String> lines) {
        // On memorise le scoreboard du joueur tel qu'il etait AVANT qu'on
        // le remplace, uniquement la toute premiere fois (sinon on finirait
        // par sauvegarder notre propre scoreboard de partie).
        previousBoards.putIfAbsent(player.getUniqueId(), player.getScoreboard());

        Scoreboard board = plugin.getServer().getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("br_sidebar", "dummy");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(truncate(title, 32));

        Set<String> usedEntries = new HashSet<>();
        int score = lines.size();

        for (String rawLine : lines) {
            String entry = truncate(rawLine, 16);
            while (usedEntries.contains(entry)) {
                entry = entry + ChatColor.RESET;
                if (entry.length() > 16) {
                    entry = entry.substring(0, 16);
                }
            }
            usedEntries.add(entry);
            obj.getScore(entry).setScore(score);
            score--;
        }

        decorate(board, player);
        player.setScoreboard(board);
    }

    /** Restaure exactement le scoreboard que le joueur avait avant le debut de la partie. */
    public void clear(Player player) {
        Scoreboard previous = previousBoards.remove(player.getUniqueId());
        player.setScoreboard(previous != null ? previous : Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /** Restaure le scoreboard d'origine de tous les joueurs en ligne (fin de partie / arret force). */
    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        // Purge les entrees residuelles de joueurs deconnectes pendant la partie.
        previousBoards.clear();
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
