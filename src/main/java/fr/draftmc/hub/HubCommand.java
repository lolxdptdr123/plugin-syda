package fr.draftmc.hub;

import fr.draftmc.Draftmc;
import fr.draftmc.util.Locations;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HubCommand implements CommandExecutor {
    private final Draftmc plugin;

    public HubCommand(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("set")) {
            if (!sender.hasPermission("draftmc.admin") || !(sender instanceof Player)) {
                plugin.msg(sender, "&cAdmin uniquement.");
                return true;
            }
            Player player = (Player) sender;
            plugin.getConfig().set("hub.location", Locations.serialize(player.getLocation()));
            plugin.saveConfig();
            plugin.msg(player, "&aHub défini ici.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        if (plugin.combat() != null && plugin.combat().denyIfTagged(player)) {
            return true;
        }
        Location loc = Locations.deserialize(plugin.getConfig().getString("hub.location"));
        if (loc == null) {
            loc = plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().get(0).getSpawnLocation();
        }
        if (loc == null) {
            plugin.msg(player, "&cHub non défini.");
            return true;
        }
        plugin.teleports().request(player, loc, plugin.getConfig().getString("hub.message", "&aTéléporté au hub."));
        return true;
    }
}
