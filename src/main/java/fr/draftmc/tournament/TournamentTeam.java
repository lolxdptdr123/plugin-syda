package fr.draftmc.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TournamentTeam {
    public enum Bracket {
        WINNERS,
        LOSERS,
        ELIMINATED
    }

    private final String name;
    private final List<UUID> members = new ArrayList<UUID>();
    private final List<UUID> invites = new ArrayList<UUID>();
    private UUID leader;
    private Bracket bracket = Bracket.WINNERS;

    public TournamentTeam(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public List<UUID> members() {
        return members;
    }

    public UUID leader() {
        if (leader == null && !members.isEmpty()) {
            leader = members.get(0);
        }
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public boolean isLeader(UUID uuid) {
        UUID current = leader();
        return current != null && current.equals(uuid);
    }

    public Bracket bracket() {
        return bracket;
    }

    public void setBracket(Bracket bracket) {
        this.bracket = bracket;
    }

    public boolean contains(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean add(UUID uuid) {
        if (members.contains(uuid)) {
            return false;
        }
        members.add(uuid);
        invites.remove(uuid);
        if (leader == null) {
            leader = uuid;
        }
        return true;
    }

    public boolean remove(UUID uuid) {
        boolean removed = members.remove(uuid);
        if (removed && uuid.equals(leader)) {
            leader = members.isEmpty() ? null : members.get(0);
        }
        return removed;
    }

    public void invite(UUID uuid) {
        if (!members.contains(uuid) && !invites.contains(uuid)) {
            invites.add(uuid);
        }
    }

    public boolean hasInvite(UUID uuid) {
        return invites.contains(uuid);
    }

    public void clearInvite(UUID uuid) {
        invites.remove(uuid);
    }

    public void clearInvites() {
        invites.clear();
    }

    public boolean eliminated() {
        return bracket == Bracket.ELIMINATED;
    }
}
