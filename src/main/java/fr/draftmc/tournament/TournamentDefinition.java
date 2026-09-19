package fr.draftmc.tournament;

public class TournamentDefinition {
    private final String name;
    private int teamSize = 1;
    private int maxTeams = 8;
    private boolean loserBracket;

    public TournamentDefinition(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public int teamSize() {
        return teamSize;
    }

    public void setTeamSize(int teamSize) {
        this.teamSize = Math.max(1, teamSize);
    }

    public int maxTeams() {
        return maxTeams;
    }

    public void setMaxTeams(int maxTeams) {
        this.maxTeams = Math.max(0, maxTeams);
    }

    public boolean loserBracket() {
        return loserBracket;
    }

    public void setLoserBracket(boolean loserBracket) {
        this.loserBracket = loserBracket;
    }
}
