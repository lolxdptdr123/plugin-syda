package fr.draftmc.util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class Chat {
    private Chat() {}

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    /**
     * Message + bouton cliquable (run_command) avec hover. Fallback texte si l'API chat echoue.
     */
    public static void sendClick(Player player, String message, String button, String hover, String command) {
        if (player == null) {
            return;
        }
        String colored = CC.color(message);
        String cmd = command == null ? "" : command.trim();
        if (!cmd.isEmpty() && cmd.charAt(0) != '/') {
            cmd = "/" + cmd;
        }
        try {
            TextComponent root = new TextComponent("");
            BaseComponent[] parts = TextComponent.fromLegacyText(colored);
            for (int i = 0; i < parts.length; i++) {
                root.addExtra(parts[i]);
            }
            if (button != null && !button.isEmpty()) {
                root.addExtra(new TextComponent(" "));
                TextComponent btn = new TextComponent(CC.color(button));
                if (!cmd.isEmpty()) {
                    btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
                }
                if (hover != null && !hover.isEmpty()) {
                    btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new ComponentBuilder(CC.color(hover)).create()));
                }
                root.addExtra(btn);
            }
            player.spigot().sendMessage(root);
        } catch (Throwable ignored) {
            player.sendMessage(colored + (cmd.isEmpty() ? "" : ChatColor.GREEN + " " + cmd));
        }
    }
}
