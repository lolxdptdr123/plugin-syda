package fr.sydaria.portals;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import fr.sydaria.util.ItemBuilder;
import fr.sydaria.util.Locations;
import fr.sydaria.util.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PortalManager implements CommandExecutor, Listener {
    private final Sydaria plugin;
    private final YamlFile file;
    private final Map<UUID, Location> pos1 = new HashMap<UUID, Location>();
    private final Map<UUID, Location> pos2 = new HashMap<UUID, Location>();

    public PortalManager(Sydaria plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "portals.yml");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sydaria.admin") || !(sender instanceof Player)) {
            plugin.msg(sender, "&cAdmin uniquement.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0) {
            plugin.msg(player, "&e/portal wand");
            plugin.msg(player, "&e/portal create <nom>");
            plugin.msg(player, "&e/portal dest <nom>");
            plugin.msg(player, "&e/portal delete <nom>");
            return true;
        }
        if (args[0].equalsIgnoreCase("wand")) {
            ItemStack wand = new ItemBuilder(Material.BLAZE_ROD)
                    .name(plugin.getConfig().getString("portals.wand-name", "&6Sélecteur de portail"))
                    .lore("&7Clic gauche: pos1", "&7Clic droit: pos2")
                    .build();
            player.getInventory().addItem(wand);
            return true;
        }
        if (args[0].equalsIgnoreCase("create") && args.length >= 2) {
            Location a = pos1.get(player.getUniqueId());
            Location b = pos2.get(player.getUniqueId());
            if (a == null || b == null) {
                plugin.msg(player, "&cSélectionne deux positions.");
                return true;
            }
            String name = args[1].toLowerCase();
            file.get().set("portals." + name + ".a", Locations.serialize(a));
            file.get().set("portals." + name + ".b", Locations.serialize(b));
            file.save();
            plugin.msg(player, "&aPortail &e" + name + " &acréé. Définis la destination: &e/portal dest " + name);
            return true;
        }
        if (args[0].equalsIgnoreCase("dest") && args.length >= 2) {
            file.get().set("portals." + args[1].toLowerCase() + ".dest", Locations.serialize(player.getLocation()));
            file.save();
            plugin.msg(player, "&aDestination enregistrée.");
            return true;
        }
        if (args[0].equalsIgnoreCase("delete") && args.length >= 2) {
            file.get().set("portals." + args[1].toLowerCase(), null);
            file.save();
            plugin.msg(player, "&cPortail supprimé.");
            return true;
        }
        return true;
    }

    @EventHandler
    public void onWand(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return;
        }
        if (!item.getItemMeta().getDisplayName().equals(CC.color(plugin.getConfig().getString("portals.wand-name", "&6Sélecteur de portail")))) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(player.getUniqueId(), event.getClickedBlock().getLocation());
            plugin.msg(player, "&aPos1 définie.");
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            pos2.put(player.getUniqueId(), event.getClickedBlock().getLocation());
            plugin.msg(player, "&aPos2 définie.");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        if (file.get().getConfigurationSection("portals") == null) {
            return;
        }
        Location loc = event.getTo();
        for (String name : file.get().getConfigurationSection("portals").getKeys(false)) {
            Location a = Locations.deserialize(file.get().getString("portals." + name + ".a"));
            Location b = Locations.deserialize(file.get().getString("portals." + name + ".b"));
            Location dest = Locations.deserialize(file.get().getString("portals." + name + ".dest"));
            if (a == null || b == null || dest == null) {
                continue;
            }
            if (inside(loc, a, b)) {
                event.getPlayer().teleport(dest);
                plugin.msg(event.getPlayer(), "&dPortail &e" + name);
                return;
            }
        }
    }

    private boolean inside(Location loc, Location a, Location b) {
        if (!loc.getWorld().equals(a.getWorld())) {
            return false;
        }
        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
