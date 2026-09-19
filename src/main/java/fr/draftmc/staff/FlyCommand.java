package fr.draftmc.staff;

import fr.draftmc.Draftmc;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FlyCommand implements CommandExecutor {
    private final Draftmc plugin;

    public FlyCommand(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("draftmc.admin")) {
            plugin.msg(sender, "&cAdmin uniquement.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        if (plugin.combat() != null && plugin.combat().isTagged(player)) {
            plugin.msg(player, "&cTu ne peux pas fly en combat.");
            return true;
        }
        boolean enable = !player.getAllowFlight();
        player.setAllowFlight(enable);
        player.setFlying(enable);
        plugin.msg(player, enable ? "&aFly activé." : "&cFly désactivé.");
        return true;
    }
}
