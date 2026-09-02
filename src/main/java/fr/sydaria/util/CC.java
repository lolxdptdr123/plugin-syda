package fr.sydaria.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public final class CC {
    private CC() {}

    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> color(List<String> lines) {
        List<String> out = new ArrayList<String>();
        if (lines == null) {
            return out;
        }
        for (String line : lines) {
            out.add(color(line));
        }
        return out;
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }

    public static String strip(String text) {
        return ChatColor.stripColor(color(text));
    }
}
