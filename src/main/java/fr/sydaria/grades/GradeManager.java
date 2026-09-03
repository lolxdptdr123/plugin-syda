package fr.sydaria.grades;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le "grade" d'un joueur (Joueur, VIP, MVP, Staff...) N'EST PAS géré par ce plugin :
 * il vient du plugin de permissions (LuckPerms / PermissionsEx) via Vault, exactement
 * comme les permissions et les commandes en plus qu'un grade débloque (ex: sydaria.fly,
 * sydaria.repair.bypass, sydaria.randomkey...). C'est la bonne pratique côté Bukkit :
 * un seul plugin (LuckPerms) gère groupes/héritage/permissions, Sydaria se contente de
 * lire le groupe primaire pour l'AFFICHER (scoreboard, chat, tab).
 *
 * Ça diffère complètement des Tags (voir TagManager) : un tag est un cosmétique que le
 * joueur choisit lui-même dans /tags, il ne donne jamais de permission ni de commande.
 */
public class GradeManager {
    private final Sydaria plugin;
    private Permission vaultPerm;

    public GradeManager(Sydaria plugin) {
        this.plugin = plugin;
        hook();
    }

    private void hook() {
        try {
            RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);
            if (rsp != null) {
                vaultPerm = rsp.getProvider();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Groupe de permission primaire (LuckPerms/PEX), en minuscule, "" si aucun plugin de perms détecté. */
    public String group(Player player) {
        if (vaultPerm == null) {
            hook();
        }
        if (vaultPerm == null || !vaultPerm.hasGroupSupport()) {
            return "";
        }
        try {
            String group = vaultPerm.getPrimaryGroup(player);
            return group == null ? "" : group.toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Nom affichable du grade (scoreboard, /grade), configurable dans scoreboard.grades.<groupe>. */
    public String displayName(Player player) {
        return gradeNameForGroup(resolveGroup(player));
    }

    /** Nom affichable d'un groupe (ex: marquis -> Marquis). */
    public String displayNameForGroup(String group) {
        return gradeNameForGroup(normalizeGroup(group));
    }

    /** Échelle des grades (partagée avec le rankup). */
    public List<String> ladder() {
        List<String> configured = plugin.getConfig().getStringList("rankup.ladder");
        if (configured == null || configured.isEmpty()) {
            return defaultLadder();
        }
        List<String> out = new ArrayList<String>();
        for (String entry : configured) {
            out.add(normalizeGroup(entry));
        }
        return out;
    }

    private List<String> defaultLadder() {
        List<String> out = new ArrayList<String>();
        out.add("default");
        out.add("chevalier");
        out.add("marquis");
        out.add("seigneur");
        out.add("empereur");
        out.add("supreme");
        out.add("star");
        return out;
    }

    public int gradeIndex(String group) {
        return ladder().indexOf(normalizeGroup(group));
    }

    /** Index du grade le plus eleve du joueur dans l'echelle (tous groupes LuckPerms confondus). */
    public int playerGradeIndex(Player player) {
        return highestGradeIndex(player);
    }

    /** Groupe le plus eleve du joueur present dans l'echelle rankup. */
    public String highestGroup(Player player) {
        List<String> ladder = ladder();
        int bestIndex = 0;
        String bestGroup = ladder.get(0);
        for (String group : allGroups(player)) {
            int index = ladder.indexOf(normalizeGroup(group));
            if (index > bestIndex) {
                bestIndex = index;
                bestGroup = ladder.get(index);
            }
        }
        return bestGroup;
    }

    private int highestGradeIndex(Player player) {
        List<String> ladder = ladder();
        int best = 0;
        for (String group : allGroups(player)) {
            int index = ladder.indexOf(normalizeGroup(group));
            if (index > best) {
                best = index;
            }
        }
        return best;
    }

    private List<String> allGroups(Player player) {
        List<String> groups = new ArrayList<String>();
        groups.add(group(player));
        if (vaultPerm == null) {
            hook();
        }
        if (vaultPerm != null && vaultPerm.hasGroupSupport()) {
            try {
                String[] fromVault = vaultPerm.getPlayerGroups(player);
                if (fromVault != null) {
                    for (String entry : fromVault) {
                        if (entry != null && !entry.isEmpty()) {
                            groups.add(entry);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return groups;
    }

    /** True si le joueur a atteint le grade minimum (ou supérieur). */
    public boolean hasMinGrade(Player player, String minGrade) {
        if (player.hasPermission("sydaria.gradecommands.bypass")) {
            return true;
        }
        int required = gradeIndex(minGrade);
        if (required < 0) {
            return false;
        }
        return highestGradeIndex(player) >= required;
    }

    /** Code couleur du grade (ex: "&d"), configurable dans scoreboard.grade-colors.<groupe>. */
    public String color(Player player) {
        String group = resolveGroup(player);
        String color = plugin.getConfig().getString("scoreboard.grade-colors." + group, "");
        return color == null ? "" : color;
    }

    /**
     * Préfixe "NomDuGrade Pseudo" entièrement dans la couleur du grade.
     * Affiche aussi le grade Joueur pour le groupe default.
     */
    public String chatPrefix(Player player) {
        String color = color(player);
        if (color.isEmpty()) {
            return "";
        }
        String name = gradeNameForGroup(resolveGroup(player));
        name = stripColor(name);
        return CC.color(color + name + " ");
    }

    private String resolveGroup(Player player) {
        return highestGroup(player);
    }

    private String normalizeGroup(String group) {
        if (group == null || group.isEmpty()) {
            return "default";
        }
        String lower = group.toLowerCase(Locale.ROOT);
        if (isDefaultGroup(lower)) {
            return "default";
        }
        return lower;
    }

    private String gradeNameForGroup(String group) {
        String mapped = plugin.getConfig().getString("scoreboard.grades." + group);
        if (mapped != null && !mapped.isEmpty()) {
            return mapped;
        }
        if (isDefaultGroup(group) || "default".equals(group)) {
            return plugin.getConfig().getString("scoreboard.grade-default", "Joueur");
        }
        return capitalize(group);
    }

    /** Retire les codes couleur (& et §) d'une chaîne, ex: "&5Supreme" -> "Supreme". */
    private String stripColor(String raw) {
        return raw.replaceAll("(?i)[&\u00A7][0-9A-FK-OR]", "");
    }

    private boolean isDefaultGroup(String group) {
        if (group == null || group.isEmpty()) {
            return true;
        }
        String lower = group.toLowerCase(Locale.ROOT);
        return "default".equals(lower) || "default_player".equals(lower)
                || "joueur".equals(lower) || "player".equals(lower)
                || "member".equals(lower) || "membre".equals(lower);
    }

    private String capitalize(String raw) {
        if (raw.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1).toLowerCase(Locale.ROOT);
    }
}
