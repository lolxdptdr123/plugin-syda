package fr.draftmc.randomtp;

import fr.draftmc.Draftmc;
import fr.draftmc.util.Cooldowns;
import fr.draftmc.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Random;

public class RandomTpCommand implements CommandExecutor {
    private final Draftmc plugin;
    private final Random random = new Random();

    public RandomTpCommand(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("draftmc.rtp")) {
            plugin.msg(player, "&cPas la permission.");
            return true;
        }
        if (plugin.combat() != null && plugin.combat().denyIfTagged(player)) {
            return true;
        }
        if (plugin.denyTpCooldown(player)) {
            return true;
        }
        int cd = plugin.getConfig().getInt("randomtp.cooldown-seconds", 60);
        if (!Cooldowns.ready(player, "rtp", cd)) {
            plugin.msg(player, "&cCooldown RTP: &e" + Cooldowns.remaining(player, "rtp") + "s");
            return true;
        }
        World world = Bukkit.getWorld(plugin.getConfig().getString("randomtp.world", player.getWorld().getName()));
        if (world == null) {
            world = player.getWorld();
        }
        int minX = plugin.getConfig().getInt("randomtp.min-x", -2000);
        int maxX = plugin.getConfig().getInt("randomtp.max-x", 2000);
        int minZ = plugin.getConfig().getInt("randomtp.min-z", -2000);
        int maxZ = plugin.getConfig().getInt("randomtp.max-z", 2000);
        int tries = plugin.getConfig().getInt("randomtp.max-tries", 30);
        for (int i = 0; i < tries; i++) {
            int x = minX + random.nextInt(Math.max(1, maxX - minX));
            int z = minZ + random.nextInt(Math.max(1, maxZ - minZ));
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            if (Locations.isSafe(loc)) {
                plugin.teleports().request(player, loc, "&aTéléporté en &e" + x + " " + y + " " + z);
                return true;
            }
        }
        plugin.msg(player, "&cAucun endroit sûr trouvé.");
        return true;
    }
}
