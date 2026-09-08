package fr.draftmc.events.battleroyal.managers;

import fr.draftmc.events.EventFactionHook;
import fr.draftmc.events.battleroyal.model.TeamBR;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gere les equipes inscrites au BR en mode "libre" (style Factions) :
 * un joueur cree sa team et en devient le leader, invite qui il veut
 * (sous reserve d'appartenir a la meme faction reelle du serveur, si
 * cette verification est activee), les invites acceptent ou refusent.
 */
public class TeamManager {

    private static final ChatColor[] COLORS = {
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.LIGHT_PURPLE, ChatColor.AQUA, ChatColor.GOLD, ChatColor.DARK_PURPLE,
            ChatColor.DARK_GREEN, ChatColor.DARK_AQUA, ChatColor.WHITE, ChatColor.GRAY
    };

    public enum CreateResult { OK, ALREADY_IN_TEAM, NAME_TAKEN, INVALID_NAME, NOT_IN_FACTION, NOT_FACTION_LEADER, FACTION_ALREADY_REGISTERED, FACTION_CHECK_UNAVAILABLE }
    public enum InviteResult { OK, NOT_LEADER, NO_TEAM, TARGET_IN_TEAM, ALREADY_INVITED, NOT_SAME_FACTION, FACTION_CHECK_UNAVAILABLE }
    public enum JoinResult { OK, NO_INVITE, AMBIGUOUS, ALREADY_IN_TEAM, TEAM_GONE }

    private EventFactionHook factionHook;
    private final boolean requireSameFaction;

    // cle = nom d'equipe en minuscule
    private final Map<String, TeamBR> teamsByName = new LinkedHashMap<>();
    private final Map<UUID, TeamBR> teamByMember = new HashMap<>();
    // joueur invite -> ensemble des noms d'equipe (minuscule) qui l'ont invite
    private final Map<UUID, Set<String>> pendingInvites = new HashMap<>();
    private int colorIndex = 0;

    public TeamManager(boolean requireSameFaction) {
        this.requireSameFaction = requireSameFaction;
    }

    /**
     * Injecte le hook Factions apres coup : la detection se fait un tick
     * apres le demarrage du serveur (voir BattleRoyal#onEnable) pour laisser
     * le temps a un plugin Factions charge sans dependance explicite
     * d'etre pleinement enregistre.
     */
    public void setEventFactionHook(EventFactionHook factionHook) {
        this.factionHook = factionHook;
    }

    public CreateResult createTeam(Player leader, String name) {
        if (teamByMember.containsKey(leader.getUniqueId())) return CreateResult.ALREADY_IN_TEAM;
        if (name == null || name.length() < 2 || name.length() > 16 || !name.matches("[A-Za-z0-9_]+")) {
            return CreateResult.INVALID_NAME;
        }
        String key = name.toLowerCase();
        if (teamsByName.containsKey(key)) return CreateResult.NAME_TAKEN;

        String factionId = null;
        if (requireSameFaction) {
            if (factionHook == null || !factionHook.isAvailable()) {
                return CreateResult.FACTION_CHECK_UNAVAILABLE;
            }
            factionId = factionHook.getFactionId(leader);
            if (factionId == null) {
                return CreateResult.NOT_IN_FACTION;
            }
            if (!factionHook.isFactionLeader(leader)) {
                return CreateResult.NOT_FACTION_LEADER;
            }
            for (TeamBR existing : teamsByName.values()) {
                if (factionId.equalsIgnoreCase(existing.getFactionId())) {
                    return CreateResult.FACTION_ALREADY_REGISTERED;
                }
            }
        }

        ChatColor color = COLORS[colorIndex % COLORS.length];
        colorIndex++;

        TeamBR team = new TeamBR(name, color, leader.getUniqueId(), factionId);
        teamsByName.put(key, team);
        teamByMember.put(leader.getUniqueId(), team);
        return CreateResult.OK;
    }

    public InviteResult invite(Player leader, Player target) {
        TeamBR team = teamByMember.get(leader.getUniqueId());
        if (team == null) return InviteResult.NO_TEAM;
        if (!team.getLeader().equals(leader.getUniqueId())) return InviteResult.NOT_LEADER;
        if (teamByMember.containsKey(target.getUniqueId())) return InviteResult.TARGET_IN_TEAM;

        if (requireSameFaction) {
            if (factionHook == null || !factionHook.isAvailable()) {
                return InviteResult.FACTION_CHECK_UNAVAILABLE;
            }
            String leaderFaction = factionHook.getFactionId(leader);
            String targetFaction = factionHook.getFactionId(target);
            if (leaderFaction == null || targetFaction == null || !leaderFaction.equals(targetFaction)) {
                return InviteResult.NOT_SAME_FACTION;
            }
        }

        Set<String> invites = pendingInvites.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashSet<>());
        if (!invites.add(team.getName().toLowerCase())) return InviteResult.ALREADY_INVITED;
        return InviteResult.OK;
    }

    /**
     * @param teamName nom de l'equipe a rejoindre, ou null si le joueur
     *                 n'a qu'une seule invitation en attente.
     */
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

        TeamBR team = teamsByName.get(key);
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

    public TeamBR getTeamOf(UUID uuid) {
        return teamByMember.get(uuid);
    }

    public Collection<TeamBR> getTeams() {
        return teamsByName.values();
    }

    /** Le joueur quitte son equipe. Si c'est le leader, l'equipe est dissoute. */
    public boolean leave(Player player) {
        TeamBR team = teamByMember.get(player.getUniqueId());
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
        TeamBR team = teamByMember.get(leader.getUniqueId());
        if (team == null || !team.getLeader().equals(leader.getUniqueId())) return false;
        if (target.getUniqueId().equals(leader.getUniqueId())) return false;
        if (!team.getMembers().contains(target.getUniqueId())) return false;

        team.getMembers().remove(target.getUniqueId());
        teamByMember.remove(target.getUniqueId());
        return true;
    }

    public boolean disband(Player leader) {
        TeamBR team = teamByMember.get(leader.getUniqueId());
        if (team == null || !team.getLeader().equals(leader.getUniqueId())) return false;
        disbandInternal(team.getName());
        return true;
    }

    private void disbandInternal(String name) {
        TeamBR team = teamsByName.remove(name.toLowerCase());
        if (team == null) return;
        for (UUID uuid : team.getMembers()) {
            teamByMember.remove(uuid);
        }
    }

    /** Reinitialise completement (utilise par /br stop). */
    public void reset() {
        teamsByName.clear();
        teamByMember.clear();
        pendingInvites.clear();
        colorIndex = 0;
    }
}
