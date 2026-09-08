package fr.draftmc.events.battleroyal.model;

import org.bukkit.ChatColor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represente une equipe inscrite au BR, creee librement par un joueur
 * (le leader), qui invite ensuite ses membres.
 */
public class TeamBR {

    private final String name;
    private final ChatColor color;
    private UUID leader;
    private final String factionId;
    private final Set<UUID> members = new LinkedHashSet<>();
    private boolean eliminated = false;

    public TeamBR(String name, ChatColor color, UUID leader, String factionId) {
        this.name = name;
        this.color = color;
        this.leader = leader;
        this.factionId = factionId;
        this.members.add(leader);
    }

    public String getFactionId() {
        return factionId;
    }

    public String getName() {
        return name;
    }

    public ChatColor getColor() {
        return color;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public String getDisplayName() {
        return color + name;
    }
}
