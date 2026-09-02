package fr.sydaria.factions;

import java.util.Locale;

public enum FactionRank {
    RECRUIT(0, "Recrue", "&7"),
    MEMBER(1, "Membre", "&f"),
    OFFICER(2, "Officier", "&e"),
    COLEADER(3, "Co-leader", "&6"),
    LEADER(4, "Leader", "&c");

    private final int weight;
    private final String display;
    private final String color;

    FactionRank(int weight, String display, String color) {
        this.weight = weight;
        this.display = display;
        this.color = color;
    }

    public int weight() {
        return weight;
    }

    public String display() {
        return display;
    }

    public String showLabel() {
        if (this == LEADER) return "Leaders";
        if (this == COLEADER) return "Co-leaders";
        if (this == OFFICER) return "Officiers";
        if (this == MEMBER) return "Membres";
        return "Recrues";
    }

    public String color() {
        return color;
    }

    public boolean isAtLeast(FactionRank other) {
        return weight >= other.weight;
    }

    public FactionRank promote() {
        if (this == RECRUIT) return MEMBER;
        if (this == MEMBER) return OFFICER;
        if (this == OFFICER) return COLEADER;
        return null;
    }

    public FactionRank demote() {
        if (this == COLEADER) return OFFICER;
        if (this == OFFICER) return MEMBER;
        if (this == MEMBER) return RECRUIT;
        return null;
    }

    public static FactionRank from(String raw) {
        if (raw == null || raw.isEmpty()) {
            return MEMBER;
        }
        String n = raw.toUpperCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        if (n.equals("COLEADER") || n.equals("COCHEF") || n.equals("COLEAD")) {
            return COLEADER;
        }
        if (n.equals("CHEF") || n.equals("LEADER")) {
            return LEADER;
        }
        if (n.equals("OFFICIER") || n.equals("OFFICER")) {
            return OFFICER;
        }
        if (n.equals("RECRUE") || n.equals("RECRUIT")) {
            return RECRUIT;
        }
        if (n.equals("MEMBRE") || n.equals("MEMBER")) {
            return MEMBER;
        }
        try {
            return valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MEMBER;
        }
    }

    public static FactionRank parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String n = raw.toUpperCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        if (n.equals("COLEADER") || n.equals("COCHEF") || n.equals("COLEAD")) {
            return COLEADER;
        }
        if (n.equals("CHEF") || n.equals("LEADER")) {
            return LEADER;
        }
        if (n.equals("OFFICIER") || n.equals("OFFICER")) {
            return OFFICER;
        }
        if (n.equals("RECRUE") || n.equals("RECRUIT")) {
            return RECRUIT;
        }
        if (n.equals("MEMBRE") || n.equals("MEMBER")) {
            return MEMBER;
        }
        return null;
    }
}
