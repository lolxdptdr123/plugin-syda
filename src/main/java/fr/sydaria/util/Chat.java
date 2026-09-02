package fr.sydaria.util;

import org.bukkit.ChatColor;

public final class Chat {
    private Chat() {}

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}
