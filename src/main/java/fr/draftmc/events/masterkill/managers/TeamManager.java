package fr.draftmc.events.masterkill.managers;

import fr.draftmc.events.masterkill.MasterKillPlugin;
import fr.draftmc.events.masterkill.model.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gere les equipes formees MANUELLEMENT par les joueurs (systeme identique
 * a DraftRoyale) : un joueur cree son equipe et en devient le leader, lui
 * seul peut inviter, les invites acceptent ou refusent. La couleur d'une
 * equipe est attribuee AUTOMATIQUEMENT (les joueurs ne la choisissent pas)
 * a partir de la liste "team-colors" de config.yml, dans l'ordre de
 * creation des equipes. Gere aussi la detection d'equipe vivante/eliminee
 * une fois le match lance.
 */
public class TeamManager {

    private static final ChatColor[] DEFAULT_COLORS = {
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.LIGHT_PURPLE, ChatColor.AQUA, ChatColor.GOLD, ChatColor.DARK_PURPLE,
            ChatColor.DARK_GREEN, ChatColor.DARK_AQUA, ChatColor.WHITE, ChatColor.GRAY
    };

    public enum CreateResult { OK, ALREADY_IN_TEAM, NAME_TAKEN, INVALID_NAME }
    public enum InviteResult { OK, NOT_LEADER, NO_TEAM, TARGET_IN_TEAM, ALREADY_INVITED }
    public enum JoinResult { OK, NO_INVITE, AMBIGUOUS, ALREADY_IN_TEAM, TEAM_GONE }

    private final MasterKillPlugin plugin;
    private final Map<String, Team> teamsByName = new LinkedHashMap<>(); // cle = nom en minuscule
    private final Map<UUID, Team> teamByMember = new HashMap<>();
    private final Map<UUID, Set<String>> pendingInvites = new HashMap<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private int colorIndex = 0;

    public TeamManager(MasterKillPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Lit "team-colors" dans config.yml (liste de noms ChatColor). Les
     * entrees invalides sont ignorees avec un avertissement en console.
     * Si la liste est absente ou vide, retombe sur une liste par defaut.
     */
    private ChatColor[] loadConfiguredColors() {
        List<String> names = plugin.getConfig().getStringList("team-colors");
        if (names.isEmpty()) return DEFAULT_COLORS;

        List<ChatColor> colors = new ArrayList<>();
        for (String name : names) {
            try {
                ChatColor color = ChatColor.valueOf(name.toUpperCase());
                if (color.isColor()) {
                    colors.add(color);
                } else {
                    plugin.getLogger().warning("[MasterKill] '" + name + "' dans team-colors n'est pas une couleur (code de formatage), ignore.");
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[MasterKill] Couleur inconnue dans team-colors : '" + name + "', ignoree.");
            }
        }
        return colors.isEmpty() ? DEFAULT_COLORS : colors.toArray(new ChatColor[0]);
    }

    public CreateResult createTeam(Player leader, String name) {
        if (teamByMember.containsKey(leader.getUniqueId())) return CreateResult.ALREADY_IN_TEAM;
        if (name == null || name.length() < 2 || name.length() > 16 || !name.matches("[A-Za-z0-9_]+")) {
            return CreateResult.INVALID_NAME;
        }
        String key = name.toLowerCase();
        if (teamsByName.containsKey(key)) return CreateResult.NAME_TAKEN;

        ChatColor[] colors = loadConfiguredColors();
        ChatColor color = colors[colorIndex % colors.length];
        colorIndex++;

        Team team = new Team(name, color, leader.getUniqueId());
        teamsByName.put(key, team);
        teamByMember.put(leader.getUniqueId(), team);
        return CreateResult.OK;
    }

    public InviteResult invite(Player leader, Player target) {
        Team team = teamByMember.get(leader.getUniqueId());
        if (team == null) return InviteResult.NO_TEAM;
        if (!team.getLeader().equals(leader.getUniqueId())) return InviteResult.NOT_LEADER;
        if (teamByMember.containsKey(target.getUniqueId())) return InviteResult.TARGET_IN_TEAM;

        Set<String> invites = pendingInvites.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashSet<>());
        if (!invites.add(team.getName().toLowerCase())) return InviteResult.ALREADY_INVITED;
        return InviteResult.OK;
    }

    public JoinResult accept(Player player, String teamName) {
        if (teamByMember.containsKey(player.getUniqueId())) return JoinResult.ALREADY_IN_TEAM;

        Set<String> invites = pendingInvites.get(player.getUniqueId());
        if (invites == null || invites.isEmpty()) return JoinResult.NO_INVITE;

        String key;
        if (teamName != null) {
            key = teamName.toLowerCase();
            if (!invites.contains(key)) return JoinResult.NO_INVITE;
        } else {
            if (invites.size() > 1) return JoinResult.AMBIGUOUS;
            key = invites.iterator().next();
        }

        Team team = teamsByName.get(key);
        if (team == null) {
            invites.remove(key);
            return JoinResult.TEAM_GONE;
        }

        team.addMember(player.getUniqueId());
        teamByMember.put(player.getUniqueId(), team);
        pendingInvites.remove(player.getUniqueId());
        return JoinResult.OK;
    }

    public boolean deny(Player player, String teamName) {
        Set<String> invites = pendingInvites.get(player.getUniqueId());
        if (invites == null || invites.isEmpty()) return false;

        if (teamName != null) {
            return invites.remove(teamName.toLowerCase());
        }
        invites.clear();
        return true;
    }

    public Set<String> getInvites(Player player) {
        return pendingInvites.getOrDefault(player.getUniqueId(), Collections.emptySet());
    }

    public Team getTeamOf(UUID uuid) {
        return teamByMember.get(uuid);
    }

    public Team getTeamOf(Player player) {
        return teamByMember.get(player.getUniqueId());
    }

    public Collection<Team> getTeams() {
        return teamsByName.values();
    }

    /** Le joueur quitte son equipe. Si c'est le leader, l'equipe est dissoute. */
    public boolean leave(Player player) {
        Team team = teamByMember.get(player.getUniqueId());
        if (team == null) return false;

        if (team.getLeader().equals(player.getUniqueId())) {
            disbandInternal(team.getName());
        } else {
            team.getMembers().remove(player.getUniqueId());
            teamByMember.remove(player.getUniqueId());
        }
        return true;
    }

    public boolean kick(Player leader, Player target) {
        Team team = teamByMember.get(leader.getUniqueId());
        if (team == null || !team.getLeader().equals(leader.getUniqueId())) return false;
        if (target.getUniqueId().equals(leader.getUniqueId())) return false;
        if (!team.getMembers().contains(target.getUniqueId())) return false;

        team.getMembers().remove(target.getUniqueId());
        teamByMember.remove(target.getUniqueId());
        return true;
    }

    public boolean disband(Player leader) {
        Team team = teamByMember.get(leader.getUniqueId());
        if (team == null || !team.getLeader().equals(leader.getUniqueId())) return false;
        disbandInternal(team.getName());
        return true;
    }

    private void disbandInternal(String name) {
        Team team = teamsByName.remove(name.toLowerCase());
        if (team == null) return;
        for (UUID uuid : team.getMembers()) {
            teamByMember.remove(uuid);
        }
    }

    // ================= Vivant / eliminee (une fois le match lance) =================

    /**
     * Marque un joueur comme elimine IMMEDIATEMENT (a appeler des la mort,
     * pas au respawn). C'est important : le mode de jeu SPECTATOR n'est
     * applique par PlayerRespawnEvent qu'au moment ou le joueur clique sur
     * "Respawn" (potentiellement plusieurs secondes apres sa mort) - se
     * baser dessus pour detecter l'elimination faisait que le match ne se
     * terminait jamais correctement.
     */
    public void markEliminated(UUID uuid) {
        eliminatedPlayers.add(uuid);
    }

    /** true si au moins un membre de cette equipe est en ligne et n'a pas ete marque elimine. */
    public boolean isTeamAlive(Team team) {
        for (UUID uuid : team.getMembers()) {
            if (eliminatedPlayers.contains(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                return true;
            }
        }
        return false;
    }

    public int countAliveTeams() {
        int count = 0;
        for (Team team : teamsByName.values()) {
            if (isTeamAlive(team)) count++;
        }
        return count;
    }

    /** @return l'unique equipe encore en vie, ou null si 0 ou plusieurs equipes sont encore en vie. */
    public Team getSoleAliveTeam() {
        Team sole = null;
        for (Team team : teamsByName.values()) {
            if (isTeamAlive(team)) {
                if (sole != null) return null;
                sole = team;
            }
        }
        return sole;
    }

    public Team getTeamByName(String name) {
        return teamsByName.get(name.toLowerCase());
    }

    /** Reinitialise completement (utilise par /masterkill stop et a la fin d'un match). */
    public void reset() {
        teamsByName.clear();
        teamByMember.clear();
        pendingInvites.clear();
        eliminatedPlayers.clear();
        colorIndex = 0;
    }
}
