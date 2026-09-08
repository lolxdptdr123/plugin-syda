package fr.draftmc.core;

import fr.draftmc.Draftmc;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminCommand implements CommandExecutor {
    private final Draftmc plugin;

    public AdminCommand(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("draftmc.admin")) {
            plugin.msg(sender, "&cPas la permission.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            plugin.msg(sender, "&6Draftmc &7v" + plugin.getDescription().getVersion());
            plugin.msg(sender, "&e/draftmc reload");
            plugin.msg(sender, "&e/draftmc questadd <joueur>");
            plugin.msg(sender, "&e/itemdraft <id> [joueur]");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            plugin.msg(sender, "&aConfiguration rechargée.");
            return true;
        }
        if (args[0].equalsIgnoreCase("questadd") && args.length >= 2) {
            Player p = Bukkit.getPlayer(args[1]);
            if (p == null) {
                plugin.msg(sender, "&cHors-ligne.");
                return true;
            }
            plugin.classement().addQuest(p);
            plugin.msg(sender, "&aQuête ajoutée à " + p.getName());
            return true;
        }
        return true;
    }
}
