package fr.draftmc.stats;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import org.bukkit.Bukkit;
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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class StatsManager implements Listener, CommandExecutor, TabCompleter {
    private static final int[] CATEGORY_SLOTS = {20, 21, 22, 29, 30, 31, 38, 39, 40};
    private final Draftmc plugin;

    public StatsManager(Draftmc plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    cacheJobLevel(online);
                }
            }
        }, 20L * 30, 20L * 120);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cacheJobLevel(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cacheJobLevel(event.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cJoueur uniquement."));
            return true;
        }
        Player player = (Player) sender;
        UUID target = player.getUniqueId();
        if (args.length > 0) {
            UUID found = findByName(args[0]);
            if (found == null) {
                plugin.msg(player, plugin.getConfig().getString("stats.unknown", "&cJoueur introuvable."));
                return true;
            }
            target = found;
        }
        openMain(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>();
        String prefix = args[0].toLowerCase();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase().startsWith(prefix)) {
                names.add(online.getName());
            }
        }
        return names;
    }

    public void openMain(Player viewer, UUID target) {
        String title = CC.color(plugin.getConfig().getString("stats.title", "&8Statistiques"));
        Inventory inv = Bukkit.createInventory(new Holder(View.MAIN, target, null, 0), 54, title);
        fill(inv);
        boolean self = viewer.getUniqueId().equals(target);
        String name = plugin.data().nameOf(target);
        String headName = self
                ? plugin.getConfig().getString("stats.your-head", "&6Tes statistiques")
                : plugin.getConfig().getString("stats.other-head", "&6Stats de &e{player}").replace("{player}", name);
        List<String> lore = new ArrayList<String>();
        for (StatType type : StatType.values()) {
            lore.add("&7" + type.display + " &8» &f" + format(type, valueOf(target, type)));
            if (type == StatType.JOB) {
                lore.addAll(jobLines(target, "  "));
            }
        }
        lore.add("");
        lore.add(plugin.getConfig().getString("stats.click-top", "&7Clique une categorie pour le top."));
        inv.setItem(13, skull(name, headName, lore));
        StatType[] types = StatType.values();
        for (int i = 0; i < types.length && i < CATEGORY_SLOTS.length; i++) {
            StatType type = types[i];
            List<String> catLore = new ArrayList<String>();
            catLore.add("&7Toi : &f" + format(type, valueOf(target, type)));
            if (type == StatType.JOB) {
                catLore.addAll(jobLines(target, ""));
            }
            catLore.add("");
            catLore.add(plugin.getConfig().getString("stats.click-top", "&eClique pour voir le top serveur."));
            List<Entry> first = top(type, 1);
            String owner = first.isEmpty() ? name : first.get(0).name;
            inv.setItem(CATEGORY_SLOTS[i], skull(owner, type.color + type.display, catLore));
        }
        inv.setItem(49, new ItemBuilder(Material.BARRIER).name("&cFermer").build());
        viewer.openInventory(inv);
    }

    public void openTop(Player viewer, UUID from, StatType type) {
        int limit = Math.max(5, plugin.getConfig().getInt("stats.top-limit", 10));
        List<Entry> top = top(type, limit);
        String title = CC.color(plugin.getConfig().getString("stats.top-title", "&8Top {stat}")
                .replace("{stat}", type.display));
        Inventory inv = Bukkit.createInventory(new Holder(View.TOP, from, type, 0), 54, title);
        fill(inv);
        int slot = 10;
        int place = 1;
        for (Entry entry : top) {
            if (slot == 17) {
                slot = 19;
            }
            if (slot == 26) {
                slot = 28;
            }
            if (slot > 34) {
                break;
            }
            List<String> lore = new ArrayList<String>();
            lore.add("&7" + type.display + " &8» &f" + format(type, entry.value));
            if (type == StatType.JOB) {
                lore.addAll(jobLines(entry.uuid, ""));
            }
            lore.add("");
            lore.add(plugin.getConfig().getString("stats.click-player", "&7Clique pour voir ses stats."));
            inv.setItem(slot, skull(entry.name, "&e#" + place + " &f" + entry.name, lore));
            slot++;
            place++;
        }
        if (top.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER).name("&cAucune donnee.").build());
        }
        inv.setItem(49, new ItemBuilder(Material.ARROW)
                .name(plugin.getConfig().getString("stats.back", "&eRetour"))
                .build());
        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Holder holder = (Holder) event.getInventory().getHolder();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }
        if (holder.view == View.MAIN) {
            if (slot == 49) {
                player.closeInventory();
                return;
            }
            for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
                if (slot == CATEGORY_SLOTS[i]) {
                    openTop(player, holder.target, StatType.values()[i]);
                    return;
                }
            }
            return;
        }
        if (slot == 49) {
            openMain(player, holder.target);
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != Material.SKULL_ITEM) {
            return;
        }
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null || meta.getOwner() == null) {
            return;
        }
        UUID found = findByName(meta.getOwner());
        if (found != null) {
            openMain(player, found);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    public double valueOf(UUID uuid, StatType type) {
        switch (type) {
            case PLAYTIME:
                return plugin.classement().playtimeSeconds(uuid);
            case MONEY:
                return plugin.economy() == null ? plugin.data().getMoney(uuid) : plugin.economy().getMoney(uuid);
            case JOB:
                return jobLevel(uuid);
            case MINED:
                return plugin.data().getInt(uuid, "blocks_mined");
            case MOBS:
                return plugin.data().getInt(uuid, "mobs_killed");
            case KILLS:
                return plugin.data().getInt(uuid, "players_killed");
            case STREAK:
                return plugin.data().getInt(uuid, "killstreak_best");
            case TOTEM:
                return plugin.data().getInt(uuid, "totem_blocks");
            case DEATHS:
                return plugin.data().getInt(uuid, "deaths");
            default:
                return 0;
        }
    }

    public String format(StatType type, double value) {
        if (type == StatType.PLAYTIME) {
            int seconds = (int) Math.max(0, value);
            int d = seconds / 86400;
            int h = (seconds % 86400) / 3600;
            int m = (seconds % 3600) / 60;
            if (d > 0) {
                return d + "j " + h + "h " + m + "m";
            }
            return h + "h " + m + "m";
        }
        if (type == StatType.MONEY) {
            return plugin.economy() == null ? ((long) Math.round(value)) + "$" : plugin.economy().format(value);
        }
        return String.valueOf((long) Math.round(value));
    }

    public List<Entry> top(StatType type, int limit) {
        List<Entry> list = new ArrayList<Entry>();
        ConfigurationSection section = plugin.data().section();
        if (section == null) {
            return list;
        }
        for (String id : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                double value = valueOf(uuid, type);
                if (value <= 0) {
                    continue;
                }
                list.add(new Entry(uuid, plugin.data().nameOf(uuid), value));
            } catch (Exception ignored) {
            }
        }
        Collections.sort(list, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Double.compare(b.value, a.value);
            }
        });
        if (list.size() > limit) {
            return new ArrayList<Entry>(list.subList(0, limit));
        }
        return list;
    }

    private int jobLevel(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            cacheJobLevel(online);
        }
        return plugin.data().getInt(uuid, "job_level");
    }

    private List<String> jobLines(UUID uuid, String prefix) {
        List<String> lines = new ArrayList<String>();
        Player online = Bukkit.getPlayer(uuid);
        if (online == null) {
            return lines;
        }
        List<String> jobs = readJobDetails(online);
        for (int i = 0; i < jobs.size() && i < 6; i++) {
            lines.add("&8" + prefix + jobs.get(i));
        }
        return lines;
    }

    private void cacheJobLevel(Player player) {
        if (player == null) {
            return;
        }
        int total = 0;
        List<String> details = readJobDetails(player);
        for (String line : details) {
            int idx = line.lastIndexOf(' ');
            if (idx > 0) {
                try {
                    total += Integer.parseInt(line.substring(idx + 1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (total > 0 || Bukkit.getPluginManager().getPlugin("Jobs") != null) {
            plugin.data().setInt(player.getUniqueId(), "job_level", total);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readJobDetails(Player player) {
        List<String> out = new ArrayList<String>();
        try {
            Plugin jobs = Bukkit.getPluginManager().getPlugin("Jobs");
            if (jobs == null || !jobs.isEnabled()) {
                return out;
            }
            Class<?> jobsCl = Class.forName("com.gamingmesh.jobs.Jobs");
            Object manager = jobsCl.getMethod("getPlayerManager").invoke(null);
            Object jp;
            try {
                jp = manager.getClass().getMethod("getJobsPlayer", Player.class).invoke(manager, player);
            } catch (NoSuchMethodException missing) {
                jp = manager.getClass().getMethod("getJobsPlayer", UUID.class).invoke(manager, player.getUniqueId());
            }
            if (jp == null) {
                return out;
            }
            List<Object> progressions = (List<Object>) jp.getClass().getMethod("getJobProgression").invoke(jp);
            if (progressions == null) {
                return out;
            }
            for (Object prog : progressions) {
                int level = ((Number) prog.getClass().getMethod("getLevel").invoke(prog)).intValue();
                Object job = prog.getClass().getMethod("getJob").invoke(prog);
                String jobName;
                try {
                    jobName = String.valueOf(job.getClass().getMethod("getName").invoke(job));
                } catch (NoSuchMethodException missing) {
                    jobName = String.valueOf(job.getClass().getMethod("getJobDisplayName").invoke(job));
                }
                out.add(jobName + " " + level);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private UUID findByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        ConfigurationSection section = plugin.data().section();
        if (section == null) {
            return null;
        }
        for (String id : section.getKeys(false)) {
            if (name.equalsIgnoreCase(section.getString(id + ".name"))) {
                try {
                    return UUID.fromString(id);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).name(" ").build();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    private ItemStack skull(String owner, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (owner != null && !owner.isEmpty()) {
            meta.setOwner(owner);
        }
        meta.setDisplayName(CC.color(name));
        meta.setLore(CC.color(lore));
        item.setItemMeta(meta);
        return item;
    }

    public enum StatType {
        PLAYTIME("Temps joué", "&a"),
        MONEY("Baltop", "&6"),
        JOB("Niveaux de métier", "&e"),
        MINED("Blocs minés", "&b"),
        MOBS("Créatures tuées", "&2"),
        KILLS("Joueurs tués", "&c"),
        STREAK("Plus longue série", "&4"),
        TOTEM("Blocs de totem cassés", "&d"),
        DEATHS("Morts", "&8");

        public final String display;
        public final String color;

        StatType(String display, String color) {
            this.display = display;
            this.color = color;
        }
    }

    public static class Entry {
        public final UUID uuid;
        public final String name;
        public final double value;

        public Entry(UUID uuid, String name, double value) {
            this.uuid = uuid;
            this.name = name;
            this.value = value;
        }
    }

    private enum View {
        MAIN,
        TOP
    }

    private static class Holder implements InventoryHolder {
        private final View view;
        private final UUID target;
        private final StatType type;

        private Holder(View view, UUID target, StatType type, int page) {
            this.view = view;
            this.target = target;
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
