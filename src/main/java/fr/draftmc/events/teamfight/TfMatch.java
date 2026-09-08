package fr.draftmc.events.teamfight;

public class TfMatch {
    private final TfTeam teamA;
    private final TfTeam teamB;
    private final String round;
    private TfTeam winner;

    public TfMatch(TfTeam teamA, TfTeam teamB, String round) {
        this.teamA = teamA;
        this.teamB = teamB;
        this.round = round;
    }

    public TfTeam getTeamA() {
        return teamA;
    }

    public TfTeam getTeamB() {
        return teamB;
    }

    public String getRound() {
        return round;
    }

    public TfTeam getWinner() {
        return winner;
    }

    public void setWinner(TfTeam winner) {
        this.winner = winner;
    }

    public TfTeam opponent(TfTeam team) {
        if (team == teamA) {
            return teamB;
        }
        if (team == teamB) {
            return teamA;
        }
        return null;
    }
}
