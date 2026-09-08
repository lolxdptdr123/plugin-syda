package fr.draftmc.events.koth;

import java.util.Locale;

public enum KothType {
    NORMAL,
    GIANT;

    public static KothType from(String raw) {
        if (raw == null || raw.isEmpty()) {
            return GIANT;
        }
        String n = raw.toLowerCase(Locale.ROOT);
        if ("normal".equals(n) || "classic".equals(n) || "koth".equals(n)) {
            return NORMAL;
        }
        return GIANT;
    }

    public String display() {
        return this == GIANT ? "geant" : "normal";
    }
}
