package fr.draftmc.events;

import java.util.Locale;

public enum EventType {
    CONQUEST("conquest", "Conquest", new String[]{"cq"}),
    DOMINATION("domination", "Domination", new String[]{"dom"}),
    BATTLEROYAL("battleroyal", "Battleroyal", new String[]{"br", "battle", "battleroyale"}),
    MASTERKILL("masterkill", "MasterKill", new String[]{"mk"}),
    TOTEM("totem", "Totem", new String[]{"tot"}),
    TOTEM_GEANT("totemgeant", "Totem Geant", new String[]{"totemg", "gianttotem"}),
    KOTH("koth", "KOTH Geant", new String[]{"kothgeant", "giantkoth", "kothg"}),
    TEAMFIGHT("teamfight", "TeamFight", new String[]{"tf", "teamf", "8v8"}),
    LARGAGE("largage", "Largage", new String[]{"airdrop", "drop", "supply"});

    private final String id;
    private final String display;
    private final String[] aliases;

    EventType(String id, String display, String[] aliases) {
        this.id = id;
        this.display = display;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    public String display() {
        return display;
    }

    public static EventType from(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String n = raw.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        for (EventType type : values()) {
            String displayNorm = type.display.toLowerCase(Locale.ROOT)
                    .replace("-", "").replace("_", "").replace(" ", "");
            if (type.id.equals(n) || displayNorm.equals(n) || type.display.equalsIgnoreCase(raw)) {
                return type;
            }
            for (int i = 0; i < type.aliases.length; i++) {
                if (type.aliases[i].equals(n)) {
                    return type;
                }
            }
        }
        return null;
    }
}
