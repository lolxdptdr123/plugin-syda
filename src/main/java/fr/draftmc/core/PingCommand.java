package fr.draftmc.core;

import fr.draftmc.Draftmc;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PingCommand implements CommandExecutor {
    private final Draftmc plugin;

    public PingCommand(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        int ping = pingOf(player);
        String color;
        if (ping < 50) {
            color = "&a";
        } else if (ping < 100) {
            color = "&e";
        } else if (ping < 200) {
            color = "&6";
        } else {
            color = "&c";
        }
        plugin.msg(player, "&7Ton ping: " + color + ping + " ms");
        return true;
    }

    public static int pingOf(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            return handle.getClass().getField("ping").getInt(handle);
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
