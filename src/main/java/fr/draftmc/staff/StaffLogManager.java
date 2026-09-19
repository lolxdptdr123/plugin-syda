package fr.draftmc.staff;

import fr.draftmc.Draftmc;
import fr.draftmc.gui.GuiHolder;
import fr.draftmc.gui.Menus;
import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import fr.draftmc.util.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Enregistre toutes les commandes des staff (helper → owner).
 * Consultation GUI : /adminlogs — admin et owner uniquement.
 */
public class StaffLogManager implements CommandExecutor, TabCompleter, Listener {
    private static final int PAGE_SIZE = 45;
    private final Draftmc plugin;
    private final YamlFile file;
    private final SimpleDateFormat format = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE);
    private int unsaved;

    public StaffLogManager(Draftmc plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "staff-logs.yml");
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                flush();
            }
        }, 20L * 30L, 20L * 30L);
    }

    public void shutdown() {
        flush();
    }

    private boolean canView(CommandSender sender) {
        return sender.hasPermission("draftmc.adminlogs") || sender.hasPermission("draftmc.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canView(sender)) {
            plugin.msg(sender, "&cAdmin / Owner uniquement.");
            return true;
        }
        if (args.length >= 1 && "clear".equalsIgnoreCase(args[0]) && args.length >= 2) {
            clearStaff(sender, args[1]);
            return true;
        }
        if (!(sender instanceof Player)) {
            listChat(sender, args.length >= 1 ? args[0] : null);
            return true;
        }
        Player player = (Player) sender;
        if (args.length >= 1) {
            String id = findStaffId(args[0]);
            if (id == null) {
                plugin.msg(player, "&cAucun log pour &e" + args[0] + "&c.");
                return true;
            }
            openLogs(player, id, 0);
            return true;
        }
        openStaffList(player, 0);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (plugin.grades() == null || !plugin.grades().isStaffMember(player)) {
            return;
        }
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) {
            return;
        }
        String cmd = firstWord(raw);
        if (ignored(cmd)) {
            return;
        }
        String path = "staff." + player.getUniqueId();
        file.get().set(path + ".name", player.getName());
        file.get().set(path + ".rank", plugin.grades().highestGroup(player));
        List<String> entries = file.get().getStringList(path + ".entries");
        entries.add(System.currentTimeMillis() + "|" + raw);
        int max = Math.max(50, plugin.getConfig().getInt("staff-logs.max-per-staff", 400));
        if (entries.size() > max) {
            entries = new ArrayList<String>(entries.subList(entries.size() - max, entries.size()));
        }
        file.get().set(path + ".entries", entries);
        unsaved++;
        if (unsaved >= 25) {
            flush();
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof GuiHolder)) {
            return;
        }
        GuiHolder gui = (GuiHolder) holder;
        if (!"staff-logs".equals(gui.menu()) && !"staff-logs-view".equals(gui.menu())) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        if (!canView(player)) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 49) {
            if ("staff-logs-view".equals(gui.menu())) {
                openStaffList(player, 0);
            } else {
                player.closeInventory();
            }
            return;
        }
        if ("staff-logs".equals(gui.menu())) {
            if (slot == 45 && gui.page() > 0) {
                openStaffList(player, gui.page() - 1);
                return;
            }
            if (slot == 53) {
                openStaffList(player, gui.page() + 1);
                return;
            }
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
                return;
            }
            List<String> lore = item.getItemMeta().getLore();
            if (lore == null || lore.isEmpty()) {
                return;
            }
            String last = CC.strip(lore.get(lore.size() - 1));
            if (last.startsWith("id:")) {
                openLogs(player, last.substring(3).trim(), 0);
            }
            return;
        }
        if (slot == 45 && gui.page() > 0) {
            openLogs(player, gui.extra(), gui.page() - 1);
            return;
        }
        if (slot == 53) {
            openLogs(player, gui.extra(), gui.page() + 1);
        }
    }

    private void openStaffList(Player viewer, int page) {
        List<String> ids = staffIds();
        int pages = Math.max(1, (ids.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page < 0) {
            page = 0;
        }
        if (page >= pages) {
            page = pages - 1;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder("staff-logs", "", page), 54,
                CC.color("&8Logs staff &7(" + (page + 1) + "/" + pages + ")"));
        Menus.fill(inv);
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < ids.size(); i++) {
            String id = ids.get(start + i);
            String name = file.get().getString("staff." + id + ".name", id);
            String rank = file.get().getString("staff." + id + ".rank", "?");
            int count = file.get().getStringList("staff." + id + ".entries").size();
            List<String> lore = new ArrayList<String>();
            lore.add("&7Grade : &e" + rank);
            lore.add("&7Commandes : &e" + count);
            lore.add("&aClique pour voir.");
            lore.add("&8id:" + id);
            inv.setItem(i, Menus.skull(name, "&e" + name, lore));
        }
        if (ids.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER).name("&7Aucun log staff").build());
        }
        if (page > 0) {
            inv.setItem(45, new ItemBuilder(Material.ARROW).name("&ePage précédente").build());
        }
        inv.setItem(49, Menus.close());
        if (page < pages - 1) {
            inv.setItem(53, new ItemBuilder(Material.ARROW).name("&ePage suivante").build());
        }
        viewer.openInventory(inv);
    }

    private void openLogs(Player viewer, String staffId, int page) {
        List<String> entries = file.get().getStringList("staff." + staffId + ".entries");
        String name = file.get().getString("staff." + staffId + ".name", staffId);
        List<String> newestFirst = new ArrayList<String>(entries);
        Collections.reverse(newestFirst);
        int pages = Math.max(1, (newestFirst.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page < 0) {
            page = 0;
        }
        if (page >= pages) {
            page = pages - 1;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder("staff-logs-view", staffId, page), 54,
                CC.color("&8" + name + " &7(" + (page + 1) + "/" + pages + ")"));
        Menus.fill(inv);
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < newestFirst.size(); i++) {
            String[] parts = splitEntry(newestFirst.get(start + i));
            String when = parts[0];
            String cmd = parts[1];
            String title = cmd.length() > 32 ? cmd.substring(0, 32) + "..." : cmd;
            inv.setItem(i, new ItemBuilder(Material.PAPER)
                    .name("&f" + title)
                    .lore("&7" + when, "&8" + cmd)
                    .build());
        }
        if (newestFirst.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER).name("&7Aucun log").build());
        }
        if (page > 0) {
            inv.setItem(45, new ItemBuilder(Material.ARROW).name("&ePage précédente").build());
        }
        inv.setItem(49, Menus.back());
        if (page < pages - 1) {
            inv.setItem(53, new ItemBuilder(Material.ARROW).name("&ePage suivante").build());
        }
        viewer.openInventory(inv);
    }

    private void listChat(CommandSender sender, String filter) {
        if (filter != null) {
            String id = findStaffId(filter);
            if (id == null) {
                plugin.msg(sender, "&cAucun log pour &e" + filter + "&c.");
                return;
            }
            List<String> entries = file.get().getStringList("staff." + id + ".entries");
            String name = file.get().getString("staff." + id + ".name", filter);
            plugin.msg(sender, "&6Logs de &e" + name + " &7(" + entries.size() + ")");
            int from = Math.max(0, entries.size() - 20);
            for (int i = entries.size() - 1; i >= from; i--) {
                String[] parts = splitEntry(entries.get(i));
                sender.sendMessage(CC.color("&8- &7" + parts[0] + " &f" + parts[1]));
            }
            return;
        }
        List<String> ids = staffIds();
        plugin.msg(sender, "&6Staff loggés : &e" + ids.size());
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            plugin.msg(sender, "&8- &e" + file.get().getString("staff." + id + ".name", id)
                    + " &7(" + file.get().getStringList("staff." + id + ".entries").size() + ")");
        }
    }

    private void clearStaff(CommandSender sender, String name) {
        String id = findStaffId(name);
        if (id == null) {
            plugin.msg(sender, "&cAucun log pour &e" + name + "&c.");
            return;
        }
        file.get().set("staff." + id, null);
        flush();
        plugin.msg(sender, "&aLogs de &e" + name + " &asupprimés.");
    }

    private List<String> staffIds() {
        List<String> ids = new ArrayList<String>();
        if (file.get().isConfigurationSection("staff")) {
            ids.addAll(file.get().getConfigurationSection("staff").getKeys(false));
        }
        return ids;
    }

    private String findStaffId(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null && file.get().isConfigurationSection("staff." + online.getUniqueId())) {
            return online.getUniqueId().toString();
        }
        List<String> ids = staffIds();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (name.equalsIgnoreCase(file.get().getString("staff." + id + ".name", ""))) {
                return id;
            }
        }
        try {
            UUID uuid = UUID.fromString(name);
            if (file.get().isConfigurationSection("staff." + uuid)) {
                return uuid.toString();
            }
        } catch (Exception ignored) {
        }
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        if (off != null && file.get().isConfigurationSection("staff." + off.getUniqueId())) {
            return off.getUniqueId().toString();
        }
        return null;
    }

    private String[] splitEntry(String raw) {
        int sep = raw.indexOf('|');
        if (sep < 0) {
            return new String[]{"?", raw};
        }
        long time;
        try {
            time = Long.parseLong(raw.substring(0, sep));
        } catch (NumberFormatException e) {
            return new String[]{"?", raw.substring(sep + 1)};
        }
        return new String[]{format.format(new Date(time)), raw.substring(sep + 1)};
    }

    private String firstWord(String raw) {
        String text = raw.startsWith("/") ? raw.substring(1) : raw;
        int space = text.indexOf(' ');
        String word = space < 0 ? text : text.substring(0, space);
        return word.toLowerCase(Locale.ROOT);
    }

    private boolean ignored(String cmd) {
        List<String> list = plugin.getConfig().getStringList("staff-logs.ignore");
        if (list == null || list.isEmpty()) {
            return "adminlogs".equals(cmd) || "alogs".equals(cmd) || "stafflogs".equals(cmd);
        }
        for (int i = 0; i < list.size(); i++) {
            if (cmd.equalsIgnoreCase(list.get(i))) {
                return true;
            }
        }
        return false;
    }

    private void flush() {
        if (unsaved <= 0) {
            return;
        }
        file.save();
        unsaved = 0;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!canView(sender) || args.length != 1) {
            return Collections.emptyList();
        }
        String token = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        if ("clear".startsWith(token)) {
            out.add("clear");
        }
        List<String> ids = staffIds();
        for (int i = 0; i < ids.size(); i++) {
            String name = file.get().getString("staff." + ids.get(i) + ".name", "");
            if (name.toLowerCase(Locale.ROOT).startsWith(token)) {
                out.add(name);
            }
        }
        return out;
    }
}
