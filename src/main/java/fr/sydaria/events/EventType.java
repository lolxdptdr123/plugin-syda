package fr.sydaria.events;

public enum EventType {
    TOTEM("Totem"),
    TOTEM_GEANT("Totem Géant"),
    KOTH("KOTH Capture"),
    KOTH_GEANT("KOTH Géant"),
    DOMINATION("Domination"),
    SANCTUAIRE("Sanctuaire"),
    DTC("DTC"),
    NEXUS("Nexus"),
    PROTECT_THE_KING("ProtectTheKing"),
    MASTERKILL("Masterkill"),
    TEAMFIGHT("TeamFight"),
    BATTLEROYAL("BattleRoyal"),
    CTF("CTF");

    private final String display;

    EventType(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public static EventType from(String raw) {
        if (raw == null) {
            return null;
        }
        String n = raw.toUpperCase().replace("-", "_").replace(" ", "_");
        if (n.equals("TOTEMGEANT") || n.equals("TOTEM_GIANT")) {
            return TOTEM_GEANT;
        }
        if (n.equals("KOTHGEANT") || n.equals("KOTH_GIANT")) {
            return KOTH_GEANT;
        }
        if (n.equals("PTK") || n.equals("KING")) {
            return PROTECT_THE_KING;
        }
        if (n.equals("BR")) {
            return BATTLEROYAL;
        }
        try {
            return EventType.valueOf(n);
        } catch (Exception e) {
            return null;
        }
    }
}
