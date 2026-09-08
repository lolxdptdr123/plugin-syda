package fr.draftmc.events.teamfight;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TfTeam {
    private final String id;
    private final String name;
    private final UUID leader;
    private final String factionId;
    private final List<UUID> members = new ArrayList<UUID>();
    private final List<UUID> invites = new ArrayList<UUID>();
    private boolean tournamentOut;

    public TfTeam(String id, String name, UUID leader, String factionId) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.factionId = factionId;
        this.members.add(leader);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getLeader() {
        return leader;
    }

    public String getFactionId() {
        return factionId;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public List<UUID> getMembers() {
        return members;
    }

    public boolean contains(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean addMember(UUID uuid, int max) {
        if (members.contains(uuid) || members.size() >= max) {
            return false;
        }
        members.add(uuid);
        invites.remove(uuid);
        return true;
    }

    public void invite(UUID uuid) {
        if (!invites.contains(uuid) && !members.contains(uuid)) {
            invites.add(uuid);
        }
    }

    public boolean hasInvite(UUID uuid) {
        return invites.contains(uuid);
    }

    public boolean isFull(int size) {
        return members.size() >= size;
    }

    public boolean isTournamentOut() {
        return tournamentOut;
    }

    public void setTournamentOut(boolean tournamentOut) {
        this.tournamentOut = tournamentOut;
    }

    public List<Player> onlineMembers() {
        List<Player> out = new ArrayList<Player>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                out.add(player);
            }
        }
        return out;
    }

    public String leaderName() {
        Player player = Bukkit.getPlayer(leader);
        if (player != null) {
            return player.getName();
        }
        return Bukkit.getOfflinePlayer(leader).getName();
    }
}
