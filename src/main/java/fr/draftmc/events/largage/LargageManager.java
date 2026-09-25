package fr.draftmc.events.largage;

import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import fr.draftmc.util.Locations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class LargageManager {
    private final LargagePlugin plugin;
    private final Random random = new Random();
    private final Set<String> chests = new HashSet<String>();
    private boolean running;
    private boolean dropped;
    private boolean pointsAwarded;
    private String pointsChest;
    private int countdown;
    private int timeLeft;
    private List<Location> pendingSpots = new ArrayList<Location>();
    private BukkitTask task;

    public LargageManager(LargagePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean running() {
        return running;
    }

    public int remainingChests() {
        return chests.size();
    }

    public int countdown() {
        return countdown;
    }

    public boolean dropped() {
        return dropped;
    }

    public int timeLeft() {
        return timeLeft;
    }

    public boolean isDropChest(Block block) {
        return block != null && chests.contains(key(block));
    }

    public void addChest(Player player) {
        List<String> spots = spots();
        spots.add(Locations.serialize(player.getLocation().getBlock().getLocation()));
        plugin.getConfig().set("chests", spots);
        plugin.getConfig().set("maps.default.world", player.getWorld().getName());
        plugin.saveConfig();
        player.sendMessage(plugin.prefix() + CC.color(plugin.getConfig()
                .getString("messages.added", "&aCoffre #{id} ajouté.")
                .replace("{id}", String.valueOf(spots.size()))));
    }

    public void removeChest(CommandSender sender, String rawId) {
        List<String> spots = spots();
        if (spots.isEmpty()) {
            sender.sendMessage(plugin.prefix() + CC.color(plugin.getConfig()
                    .getString("messages.no-chests", "&cAucun coffre configuré. &e/largage add")));
            return;
        }
        int index = -1;
        if (rawId != null && !rawId.isEmpty()) {
            try {
                index = Integer.parseInt(rawId) - 1;
            } catch (NumberFormatException ignored) {
            }
        } else if (sender instanceof Player) {
            index = nearestIndex((Player) sender, spots);
        }
        if (index < 0 || index >= spots.size()) {
            sender.sendMessage(plugin.prefix() + CC.color(plugin.getConfig()
                    .getString("messages.invalid-id", "&cID invalide. &e/largage list")));
            return;
        }
        spots.remove(index);
        plugin.getConfig().set("chests", spots);
        plugin.saveConfig();
        sender.sendMessage(plugin.prefix() + CC.color(plugin.getConfig()
                .getString("messages.removed", "&cCoffre #{id} retiré.")
                .replace("{id}", String.valueOf(index + 1))));
    }

    public void listChests(CommandSender sender) {
        List<String> spots = spots();
        if (spots.isEmpty()) {
            sender.sendMessage(plugin.prefix() + CC.color(plugin.getConfig()
                    .getString("messages.no-chests", "&cAucun coffre configuré. &e/largage add")));
            return;
        }
        sender.sendMessage(plugin.prefix() + CC.color("&6Coffres &7(" + spots.size() + ")"));
        for (int i = 0; i < spots.size(); i++) {
            Location loc = Locations.deserialize(spots.get(i));
            if (loc == null || loc.getWorld() == null) {
                sender.sendMessage(CC.color("&e" + (i + 1) + ". &cmonde introuvable &8- &7" + spots.get(i)));
                continue;
            }
            sender.sendMessage(CC.color("&e" + (i + 1) + ". &f" + loc.getWorld().getName()
                    + " &7" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()));
        }
    }

    public void clearChests(CommandSender sender) {
        plugin.getConfig().set("chests", new ArrayList<String>());
        plugin.saveConfig();
        sender.sendMessage(plugin.prefix() + CC.color(plugin.getConfig()
                .getString("messages.cleared", "&7Tous les points ont été retirés.")));
    }

    public boolean start() {
        if (running) {
            return false;
        }
        List<Location> spots = loadSpots();
        if (spots.isEmpty()) {
            return false;
        }
        running = true;
        dropped = false;
        pointsAwarded = false;
        pointsChest = null;
        pendingSpots = spots;
        countdown = Math.max(0, plugin.getConfig().getInt("countdown-seconds", 60));
        timeLeft = Math.max(30, plugin.getConfig().getInt("duration-seconds", 600));
        if (plugin.getScoreboard() != null) {
            plugin.getScoreboard().start();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin.getHost(), new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 0L, 20L);
        if (countdown <= 0) {
            spawnChests();
        }
        return true;
    }

    public boolean stop(boolean announce) {
        if (!running) {
            return false;
        }
        running = false;
        dropped = false;
        pointsAwarded = false;
        pointsChest = null;
        countdown = 0;
        pendingSpots = new ArrayList<Location>();
        if (task != null) {
            task.cancel();
            task = null;
        }
        List<String> copy = new ArrayList<String>(chests);
        for (int i = 0; i < copy.size(); i++) {
            Block block = blockOf(copy.get(i));
            if (block != null) {
                removeChest(block, false);
            }
        }
        chests.clear();
        if (plugin.getScoreboard() != null) {
            plugin.getScoreboard().stop();
        }
        if (announce) {
            Bukkit.broadcastMessage(plugin.prefix() + CC.color(plugin.getConfig()
                    .getString("messages.stopped", "&7Le largage est terminé.")));
        }
        return true;
    }

    public void removeChest(Block block, boolean fromLoot) {
        if (block == null || !chests.remove(key(block))) {
            return;
        }
        if (block.getType() == Material.CHEST) {
            Chest chest = (Chest) block.getState();
            chest.getInventory().clear();
            block.setType(Material.AIR);
        }
        if (fromLoot && running && chests.isEmpty()) {
            stop(true);
            if (plugin.getHost().events() != null) {
                plugin.getHost().events().clearActive(fr.draftmc.events.EventType.LARGAGE, "default");
            }
        }
    }

    private void tick() {
        if (!running) {
            return;
        }
        if (!dropped) {
            if (countdown > 0) {
                announceCountdown();
                countdown--;
                return;
            }
            spawnChests();
            return;
        }
        timeLeft--;
        if (timeLeft <= 0 || chests.isEmpty()) {
            stop(true);
            if (plugin.getHost().events() != null) {
                plugin.getHost().events().clearActive(fr.draftmc.events.EventType.LARGAGE, "default");
            }
        }
    }

    private void announceCountdown() {
        if (countdown > 10 && countdown % 10 != 0) {
            return;
        }
        Bukkit.broadcastMessage(plugin.prefix() + CC.color(plugin.getConfig()
                .getString("messages.countdown", "&eLargage dans &f{time}&e.")
                .replace("{time}", countdown + "s")));
        float pitch = countdown <= 10 ? 1.6f : 1.0f;
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.NOTE_PLING, 0.7f, pitch);
        }
    }

    private void spawnChests() {
        if (dropped) {
            return;
        }
        dropped = true;
        countdown = 0;
        for (int i = 0; i < pendingSpots.size(); i++) {
            placeChest(pendingSpots.get(i));
        }
        pickPointsChest();
        Bukkit.broadcastMessage(plugin.prefix() + CC.color(plugin.getConfig()
                .getString("messages.started", "&e{count} &7coffres de largage sont apparus !")
                .replace("{count}", String.valueOf(chests.size()))));
        if (!pendingSpots.isEmpty()) {
            playCue(pendingSpots.get(0));
        }
        pendingSpots = new ArrayList<Location>();
        if (chests.isEmpty()) {
            stop(true);
            if (plugin.getHost().events() != null) {
                plugin.getHost().events().clearActive(fr.draftmc.events.EventType.LARGAGE, "default");
            }
        }
    }

    public boolean lootChest(Player player, Block block) {
        if (player == null || !isDropChest(block) || block.getType() != Material.CHEST) {
            return false;
        }
        Chest chest = (Chest) block.getState();
        ItemStack[] contents = chest.getInventory().getContents();
        Location dropAt = player.getLocation();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }
            java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            if (leftover != null && dropAt.getWorld() != null) {
                for (ItemStack extra : leftover.values()) {
                    if (extra != null && extra.getAmount() > 0) {
                        dropAt.getWorld().dropItemNaturally(dropAt, extra);
                    }
                }
            }
        }
        chest.getInventory().clear();
        player.updateInventory();
        player.playSound(player.getLocation(), Sound.CHEST_CLOSE, 1f, 1.2f);
        boolean lucky = pointsChest != null && pointsChest.equals(key(block));
        if (lucky) {
            awardRankingPoint(player);
        }
        removeChest(block, true);
        if (lucky && !pointsAwarded && !chests.isEmpty()) {
            pickPointsChest();
        }
        return true;
    }

    private void pickPointsChest() {
        pointsChest = null;
        if (chests.isEmpty() || plugin.getConfig().getInt("ranking-points", 1) <= 0) {
            return;
        }
        List<String> keys = new ArrayList<String>(chests);
        pointsChest = keys.get(random.nextInt(keys.size()));
    }

    private void awardRankingPoint(Player player) {
        if (pointsAwarded) {
            return;
        }
        int amount = Math.max(1, plugin.getConfig().getInt("ranking-points", 1));
        if (plugin.getHost().factions() == null) {
            return;
        }
        String fac = plugin.getHost().factions().factionOf(player);
        if (fac == null || fac.isEmpty()) {
            plugin.getHost().msg(player, "&cTu dois être dans une faction pour recevoir le point classement.");
            return;
        }
        pointsAwarded = true;
        plugin.getHost().factions().addTopPoints(fac, amount);
        String facName = plugin.getHost().factions().displayName(fac);
        Bukkit.broadcastMessage(plugin.prefix() + CC.color(plugin.getConfig()
                .getString("messages.ranking-point",
                        "&e{player} &7a obtenu &6{points} point(s) classement &7pour &e{faction}&7.")
                .replace("{player}", player.getName())
                .replace("{points}", String.valueOf(amount))
                .replace("{faction}", facName)));
        if (plugin.getHost().events() != null) {
            plugin.getHost().events().announceDiscordWinner(
                    fr.draftmc.events.EventType.LARGAGE, "default", facName, amount);
        }
    }

    private void placeChest(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        loc.getWorld().getChunkAt(loc).load();
        Block target = loc.getBlock();
        if (chests.contains(key(target))) {
            return;
        }
        target.setType(Material.CHEST);
        if (!(target.getState() instanceof Chest)) {
            return;
        }
        Chest chest = (Chest) target.getState();
        fill(chest);
        chests.add(key(target));
    }

    private void fill(Chest chest) {
        int min = Math.max(1, plugin.getConfig().getInt("items-per-chest-min", 3));
        int max = Math.max(min, plugin.getConfig().getInt("items-per-chest-max", 6));
        int count = min + random.nextInt(max - min + 1);
        for (int i = 0; i < count; i++) {
            ItemStack item = rollLoot();
            if (item != null) {
                chest.getInventory().addItem(item);
            }
        }
    }

    private ItemStack rollLoot() {
        List<org.bukkit.configuration.ConfigurationSection> entries = new ArrayList<ConfigurationSection>();
        List<java.util.Map<?, ?>> raw = plugin.getConfig().getMapList("loot");
        int total = 0;
        for (int i = 0; i < raw.size(); i++) {
            org.bukkit.configuration.MemoryConfiguration mem = new org.bukkit.configuration.MemoryConfiguration();
            ConfigurationSection sec = mem.createSection("e", raw.get(i));
            int w = Math.max(1, sec.getInt("weight", 1));
            total += w;
            entries.add(sec);
        }
        if (entries.isEmpty() || total <= 0) {
            return gear("sword");
        }
        int pick = random.nextInt(total);
        int acc = 0;
        ConfigurationSection chosen = entries.get(0);
        for (int i = 0; i < entries.size(); i++) {
            acc += Math.max(1, entries.get(i).getInt("weight", 1));
            if (pick < acc) {
                chosen = entries.get(i);
                break;
            }
        }
        return itemFrom(chosen);
    }

    private ItemStack itemFrom(ConfigurationSection sec) {
        String type = sec.getString("type", "vanilla").toLowerCase(Locale.ROOT);
        if ("custom".equals(type)) {
            if (plugin.getHost().items() == null) {
                return null;
            }
            return plugin.getHost().items().create(sec.getString("id", ""));
        }
        if ("gear".equals(type)) {
            return gear(sec.getString("piece", "sword"));
        }
        Material mat = Material.matchMaterial(sec.getString("material", "STONE"));
        if (mat == null) {
            mat = Material.STONE;
        }
        int amount = Math.max(1, sec.getInt("amount", 1));
        short data = (short) sec.getInt("data", 0);
        return new ItemStack(mat, amount, data);
    }

    private ItemStack gear(String piece) {
        String id = piece == null ? "sword" : piece.toLowerCase(Locale.ROOT);
        if ("sword".equals(id)) {
            return new ItemBuilder(Material.DIAMOND_SWORD)
                    .name("&bÉpée T5U3F2")
                    .enchant(Enchantment.DAMAGE_ALL, 5)
                    .enchant(Enchantment.DURABILITY, 3)
                    .enchant(Enchantment.FIRE_ASPECT, 2)
                    .build();
        }
        Material mat = Material.DIAMOND_HELMET;
        String name = "&bCasque P4U3";
        if ("chestplate".equals(id) || "chest".equals(id)) {
            mat = Material.DIAMOND_CHESTPLATE;
            name = "&bPlastron P4U3";
        } else if ("leggings".equals(id) || "legs".equals(id)) {
            mat = Material.DIAMOND_LEGGINGS;
            name = "&bJambières P4U3";
        } else if ("boots".equals(id)) {
            mat = Material.DIAMOND_BOOTS;
            name = "&bBottes P4U3";
        }
        return new ItemBuilder(mat)
                .name(name)
                .enchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4)
                .enchant(Enchantment.DURABILITY, 3)
                .build();
    }

    private List<String> spots() {
        return new ArrayList<String>(plugin.getConfig().getStringList("chests"));
    }

    public Location firstSpot() {
        List<Location> spots = loadSpots();
        return spots.isEmpty() ? null : spots.get(0).clone();
    }

    private List<Location> loadSpots() {
        List<Location> out = new ArrayList<Location>();
        Set<String> used = new HashSet<String>();
        List<String> raw = spots();
        for (int i = 0; i < raw.size(); i++) {
            Location loc = Locations.deserialize(raw.get(i));
            if (loc == null || loc.getWorld() == null) {
                continue;
            }
            String id = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
            if (!used.add(id)) {
                continue;
            }
            out.add(loc);
        }
        return out;
    }

    private int nearestIndex(Player player, List<String> spots) {
        int best = -1;
        double bestDist = 16;
        Location here = player.getLocation();
        for (int i = 0; i < spots.size(); i++) {
            Location loc = Locations.deserialize(spots.get(i));
            if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(here.getWorld())) {
                continue;
            }
            double dist = loc.distanceSquared(here);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private void playCue(Location loc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(loc.getWorld())) {
                player.playSound(player.getLocation(), Sound.WITHER_SPAWN, 0.4f, 1.4f);
            }
        }
        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, Sound.EXPLODE, 1.5f, 0.8f);
        }
    }

    private String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private Block blockOf(String key) {
        String[] p = key.split(":");
        if (p.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(p[0]);
        if (world == null) {
            return null;
        }
        return world.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }
}
