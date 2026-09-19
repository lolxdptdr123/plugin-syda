package fr.draftmc.tournament;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TournamentMatch {
    public enum Kind {
        WINNERS,
        LOSERS,
        GRAND_FINAL
    }

    private final TournamentTeam a;
    private final TournamentTeam b;
    private final TournamentArena arena;
    private final Kind kind;
    private final Set<UUID> dead = new HashSet<UUID>();
    private int countdown;
    private boolean fighting;

    public TournamentMatch(TournamentTeam a, TournamentTeam b, TournamentArena arena, Kind kind, int countdown) {
        this.a = a;
        this.b = b;
        this.arena = arena;
        this.kind = kind;
        this.countdown = countdown;
    }

    public TournamentTeam a() {
        return a;
    }

    public TournamentTeam b() {
        return b;
    }

    public TournamentArena arena() {
        return arena;
    }

    public Kind kind() {
        return kind;
    }

    public Set<UUID> dead() {
        return dead;
    }

    public int countdown() {
        return countdown;
    }

    public void tickCountdown() {
        if (countdown > 0) {
            countdown--;
        }
    }

    public boolean fighting() {
        return fighting;
    }

    public void setFighting(boolean fighting) {
        this.fighting = fighting;
    }

    public boolean hasPlayer(UUID uuid) {
        return a.contains(uuid) || b.contains(uuid);
    }

    public TournamentTeam teamOf(UUID uuid) {
        if (a.contains(uuid)) {
            return a;
        }
        if (b.contains(uuid)) {
            return b;
        }
        return null;
    }

    public TournamentTeam opponent(TournamentTeam team) {
        return team == a ? b : a;
    }

    public boolean alive(TournamentTeam team) {
        for (UUID uuid : team.members()) {
            if (!dead.contains(uuid)) {
                return true;
            }
        }
        return false;
    }
}
