package fr.draftmc.outpost;

import fr.draftmc.Draftmc;
import fr.draftmc.gui.GuiHolder;
import fr.draftmc.gui.Menus;
import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import fr.draftmc.util.Locations;
import fr.draftmc.util.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OutpostManager implements CommandExecutor, TabCompleter, Listener {
    private final Draftmc plugin;
    private final YamlFile file;
    private final Map<String, Integer> capturing = new HashMap<String, Integer>();

    public OutpostManager(Draftmc plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "outposts.yml");
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && sender.hasPermission("draftmc.admin")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cJoueur uniquement.");
                return true;
            }
            Player player = (Player) sender;
            if (args[0].equalsIgnoreCase("set") && args.length >= 2) {
                setCorner(player, args[1], args.length >= 3 ? args[2] : "pos1");
                return true;
            }
            if (args[0].equalsIgnoreCase("lock") && args.length >= 3) {
                file.get().set(args[1].toLowerCase(Locale.ROOT) + ".lock-until",
                        System.currentTimeMillis() + 1000L * parse(args[2], 300));
                file.save();
                plugin.msg(player, "&aLock défini.");
                return true;
            }
        }
        if (!(sender instanceof Player)) {
            return true;
        }
        open((Player) sender);
        return true;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new GuiHolder("outpost"), 27, CC.color("&8Outposts"));
        Menus.fill(inv);
        List<String> ids = outpostIds();
        int[] slots = {11, 15};
        for (int i = 0; i < ids.size() && i < slots.length; i++) {
            inv.setItem(slots[i], icon(ids.get(i)));
        }
        inv.setItem(22, Menus.close());
        player.openInventory(inv);
    }

    private ItemStack icon(String id) {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("outposts.list." + id);
        String name = cfg != null ? cfg.getString("name", "&e" + id) : "&e" + id;
        Material mat = Material.BEACON;
        if (cfg != null) {
            try {
                mat = Material.valueOf(cfg.getString("material", "BEACON").toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
            }
        }
        String owner = file.get().getString(id + ".owner", "");
        long lock = file.get().getLong(id + ".lock-until", 0L);
        boolean locked = lock > System.currentTimeMillis();
        List<String> lore = new ArrayList<String>();
        lore.add("&7Contrôle : " + (owner.isEmpty() ? "&cPersonne" : "&e" + plugin.factions().displayName(owner)));
        if (locked) {
            lore.add("&cLock : &e" + formatLeft(lock - System.currentTimeMillis()));
        } else {
            lore.add("&aCapturable");
            Integer progress = capturing.get(id);
            if (progress != null) {
                lore.add("&7Capture : &e" + progress + "s");
            }
        }
        lore.add("&7Reste dans la zone pour capturer.");
        return new ItemBuilder(mat).name(name).lore(lore).build();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiHolder) || !"outpost".equals(((GuiHolder) holder).menu())) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() == 22) {
            event.getWhoClicked().closeInventory();
        }
    }

    private void tick() {
        if (plugin.factions() == null) {
            return;
        }
        int need = plugin.getConfig().getInt("outposts.capture-seconds", 30);
        for (String id : outpostIds()) {
            long lock = file.get().getLong(id + ".lock-until", 0L);
            if (lock > System.currentTimeMillis()) {
                capturing.remove(id);
                continue;
            }
            Location a = Locations.deserialize(file.get().getString(id + ".pos1"));
            Location b = Locations.deserialize(file.get().getString(id + ".pos2"));
            if (a == null || b == null) {
                continue;
            }
            Map<String, Integer> counts = new HashMap<String, Integer>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!inside(player.getLocation(), a, b)) {
                    continue;
                }
                String fac = plugin.factions().factionOf(player);
                if (fac.isEmpty()) {
                    continue;
                }
                Integer n = counts.get(fac);
                counts.put(fac, n == null ? 1 : n + 1);
            }
            String best = null;
            int bestN = 0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (e.getValue() > bestN) {
                    best = e.getKey();
                    bestN = e.getValue();
                }
            }
            String owner = file.get().getString(id + ".owner", "");
            if (best == null || best.equalsIgnoreCase(owner)) {
                capturing.remove(id);
                continue;
            }
            Integer now = capturing.get(id);
            int next = now == null ? 1 : now + 1;
            capturing.put(id, next);
            if (next >= need) {
                capturing.remove(id);
                file.get().set(id + ".owner", best);
                int lockSec = plugin.getConfig().getInt("outposts.lock-seconds", 600);
                file.get().set(id + ".lock-until", System.currentTimeMillis() + 1000L * lockSec);
                file.save();
                String display = plugin.factions().displayName(best);
                Bukkit.broadcastMessage(CC.color(plugin.prefix() + "&e" + display
                        + " &7a capturé l'outpost &6" + id + "&7 !"));
            }
        }
    }

    private void setCorner(Player player, String id, String corner) {
        id = id.toLowerCase(Locale.ROOT);
        String key = corner.equalsIgnoreCase("pos2") ? "pos2" : "pos1";
        file.get().set(id + "." + key, Locations.serialize(player.getLocation()));
        file.save();
        plugin.msg(player, "&aOutpost &e" + id + " &7" + key + " défini.");
    }

    private List<String> outpostIds() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("outposts.list");
        if (section != null && !section.getKeys(false).isEmpty()) {
            return new ArrayList<String>(section.getKeys(false));
        }
        return Arrays.asList("pvp", "farm");
    }

    private boolean inside(Location loc, Location a, Location b) {
        if (loc.getWorld() == null || a.getWorld() == null || !loc.getWorld().equals(a.getWorld())) {
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

    private String formatLeft(long ms) {
        int sec = (int) (ms / 1000L);
        int m = sec / 60;
        int s = sec % 60;
        return m + "m " + s + "s";
    }

    private int parse(String raw, int def) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("draftmc.admin")) {
            return new ArrayList<String>();
        }
        if (args.length == 1) {
            return Arrays.asList("set", "lock");
        }
        if (args.length == 2) {
            return outpostIds();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("pos1", "pos2");
        }
        return new ArrayList<String>();
    }
}
