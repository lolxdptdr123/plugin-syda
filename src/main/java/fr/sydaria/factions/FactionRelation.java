package fr.sydaria.factions;

public enum FactionRelation {
    MEMBER("&a"),
    NEUTRAL("&7"),
    ENEMY("&c");

    private final String color;

    FactionRelation(String color) {
        this.color = color;
    }

    public String color() {
        return color;
    }

    public static FactionRelation from(String raw) {
        if (raw == null || raw.isEmpty()) {
            return NEUTRAL;
        }
        try {
            return valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NEUTRAL;
        }
    }
}
