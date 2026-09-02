package fr.sydaria.util;

import org.bukkit.entity.Player;

public final class ActionBars {

    private ActionBars() {}

    public static void send(Player player, String message) {
        if (player == null || !player.isOnline()) {
            return;
        }
        NMS.actionBar(player, message);
    }
}