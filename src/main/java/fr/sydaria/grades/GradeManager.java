package fr.sydaria.grades;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

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
        String group = group(player);
        if (!group.isEmpty()) {
            String mapped = plugin.getConfig().getString("scoreboard.grades." + group);
            if (mapped != null && !mapped.isEmpty()) {
                return mapped;
            }
            if (!isDefaultGroup(group)) {
                return capitalize(group);
            }
        }
        String tag = plugin.tags().selectedName(player);
        if (tag != null && !tag.isEmpty()) {
            return tag;
        }
        return plugin.getConfig().getString("scoreboard.grade-default", "Joueur");
    }

    /**
     * Code couleur du grade (ex: "&d"), configurable dans scoreboard.grade-colors.<groupe>.
     * Vide pour le groupe par défaut ou si aucune couleur n'est définie.
     */
    public String color(Player player) {
        String group = group(player);
        if (group.isEmpty() || isDefaultGroup(group)) {
            return "";
        }
        String color = plugin.getConfig().getString("scoreboard.grade-colors." + group, "");
        return color == null ? "" : color;
    }

    /**
     * Préfixe "NomDuGrade Pseudo" entièrement dans la couleur du grade, ex: pour un
     * joueur du groupe "star" avec la couleur "&d" ça donne "&dStar " : le code couleur
     * n'est PAS suivi d'un reset, donc le pseudo qui suit juste après hérite de la même
     * couleur automatiquement (comportement standard de Minecraft), et le résultat
     * affiché est "Star Pseudo" entièrement en violet clair.
     *
     * Vide (donc rien devant le pseudo) pour le groupe par défaut, ou si aucune couleur
     * n'est configurée pour ce groupe dans scoreboard.grade-colors.
     */
    public String chatPrefix(Player player) {
        String group = group(player);
        if (group.isEmpty() || isDefaultGroup(group)) {
            return "";
        }
        String color = color(player);
        if (color.isEmpty()) {
            return "";
        }
        String name = plugin.getConfig().getString("scoreboard.grades." + group);
        if (name == null || name.isEmpty()) {
            name = capitalize(group);
        }
        // Certains noms de grade (scoreboard.grades.<groupe>) embarquent déjà leur
        // propre couleur pour la ligne "Grade:" du scoreboard (ex: "&5Supreme"). On
        // la retire ici pour que ce soit TOUJOURS la couleur de grade-colors qui
        // s'applique dans le chat/tab/pseudo, sans exception ni conflit possible.
        name = stripColor(name);
        return CC.color(color + name + " ");
    }

    /** Retire les codes couleur (& et §) d'une chaîne, ex: "&5Supreme" -> "Supreme". */
    private String stripColor(String raw) {
        return raw.replaceAll("(?i)[&\u00A7][0-9A-FK-OR]", "");
    }

    private boolean isDefaultGroup(String group) {
        return "default".equalsIgnoreCase(group) || "default_player".equalsIgnoreCase(group);
    }

    private String capitalize(String raw) {
        if (raw.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1).toLowerCase(Locale.ROOT);
    }
}
