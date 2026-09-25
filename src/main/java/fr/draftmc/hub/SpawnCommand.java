package fr.draftmc.hub;

import fr.draftmc.Draftmc;
import fr.draftmc.util.Items;
import fr.draftmc.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class SpawnCommand implements CommandExecutor, Listener {
    private static final String BOOT_KEY = "spawn_boot";
    private final Draftmc plugin;
    private final long bootId;

    public SpawnCommand(Draftmc plugin) {
        this.plugin = plugin;
        this.bootId = System.currentTimeMillis();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("set")) {
            if (!sender.hasPermission("draftmc.admin") || !(sender instanceof Player)) {
                plugin.msg(sender, "&cAdmin uniquement.");
                return true;
            }
            Player player = (Player) sender;
            Location loc = player.getLocation();
            plugin.getConfig().set("spawn.world", loc.getWorld().getName());
            plugin.getConfig().set("spawn.location", Locations.serialize(loc));
            plugin.saveConfig();
            plugin.msg(player, "&aSpawn défini ici &7(" + loc.getWorld().getName() + "&7).");
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
        Location loc = spawnLocation();
        if (loc == null) {
            plugin.msg(player, "&cSpawn non défini. &7Un admin doit faire &e/spawn set &7dans le monde voulu.");
            return true;
        }
        plugin.teleports().request(player, loc, plugin.getConfig().getString("spawn.message", "&aTéléporté au spawn."));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        boolean firstJoin = plugin.getConfig().getBoolean("spawn.on-first-join", true) && !player.hasPlayedBefore();
        boolean afterRestart = plugin.getConfig().getBoolean("spawn.on-server-restart", true)
                && plugin.data().getLong(player.getUniqueId(), BOOT_KEY) != bootId;
        if (!firstJoin && !afterRestart) {
            plugin.data().setLong(player.getUniqueId(), BOOT_KEY, bootId);
            return;
        }
        final Location loc = spawnLocation();
        if (loc == null) {
            return;
        }
        plugin.data().setLong(player.getUniqueId(), BOOT_KEY, bootId);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                PlayerInventory inv = player.getInventory();
                final ItemStack[] contents = cloneItems(inv.getContents());
                final ItemStack[] armor = cloneItems(inv.getArmorContents());
                final boolean hadItems = hasAnyItem(contents) || hasAnyItem(armor);
                player.teleport(loc);
                Items.resyncClient(plugin, player);
                Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (!player.isOnline()) {
                            return;
                        }
                        PlayerInventory now = player.getInventory();
                        if (hadItems && !hasAnyItem(now.getContents()) && !hasAnyItem(now.getArmorContents())) {
                            now.setContents(contents);
                            now.setArmorContents(armor);
                        }
                        player.updateInventory();
                    }
                }, 5L);
            }
        }, 5L);
    }

    private static boolean hasAnyItem(ItemStack[] items) {
        if (items == null) {
            return false;
        }
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getType() != org.bukkit.Material.AIR) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    public Location spawnLocation() {
        return resolve(plugin);
    }

    /** Position de /spawn set (config.yml), sinon spawn du monde spawn.world. */
    public static Location resolve(Draftmc plugin) {
        if (plugin == null) {
            return null;
        }
        Location set = Locations.deserialize(plugin.getConfig().getString("spawn.location"));
        if (set != null && set.getWorld() != null) {
            return set;
        }
        String worldName = plugin.getConfig().getString("spawn.world", "world");
        World world = worldName == null || worldName.isEmpty() ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return world.getSpawnLocation();
    }
}
