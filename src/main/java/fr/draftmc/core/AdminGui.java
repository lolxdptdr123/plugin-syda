package fr.draftmc.core;

import fr.draftmc.Draftmc;
import fr.draftmc.gui.GuiHolder;
import fr.draftmc.gui.Menus;
import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.List;

public class AdminGui implements Listener {
    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private final Draftmc plugin;

    public AdminGui(Draftmc plugin) {
        this.plugin = plugin;
    }

    public boolean canOpen(Player player) {
        return player.hasPermission("draftmc.staff") || player.hasPermission("draftmc.admin");
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(new GuiHolder("admin-main"), 54, CC.color("&8Sysadmin Draftmc"));
        Menus.fill(inv);
        inv.setItem(4, new ItemBuilder(Material.REDSTONE_COMPARATOR)
                .name("&c&lSysadmin")
                .lore("&7Outils staff et admin.", "&7Les catégories suivent tes permissions.").build());
        List<String> cats = visibleCategories(player);
        for (int i = 0; i < cats.size() && i < SLOTS.length; i++) {
            String id = cats.get(i);
            ConfigurationSection sec = section(id);
            if (sec == null) {
                continue;
            }
            inv.setItem(SLOTS[i], new ItemBuilder(material(sec.getString("material", "COMMAND")))
                    .name(sec.getString("name", "&e" + id))
                    .lore(loreOr(sec, "&7Clique pour voir les commandes."))
                    .build());
        }
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
    }

    public void openCategory(Player player, String category) {
        ConfigurationSection sec = section(category);
        if (sec == null || !canSeeCategory(player, sec)) {
            openMain(player);
            return;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder("admin-cat", category), 54,
                CC.color(sec.getString("title", sec.getString("name", "&8Admin"))));
        Menus.fill(inv);
        int slot = 10;
        for (String line : visibleCommands(player, sec)) {
            String[] parts = split(line);
            String cmd = parts[0];
            String desc = parts.length > 1 ? parts[1] : "";
            String run = parts.length > 2 ? parts[2] : "";
            List<String> lore = new ArrayList<String>();
            lore.add("&7" + desc);
            lore.add("");
            if (!run.isEmpty()) {
                lore.add("&aClique pour lancer.");
            } else {
                lore.add("&eClique pour copier l'usage en chat.");
            }
            inv.setItem(slot, new ItemBuilder(Material.PAPER)
                    .name("&e" + cmd)
                    .lore(lore)
                    .build());
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= 44) {
                break;
            }
        }
        inv.setItem(45, Menus.back());
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
    }

    public void sendTextHelp(org.bukkit.command.CommandSender sender) {
        plugin.msg(sender, "&8&m-----&r &cSysadmin Draftmc &8&m-----");
        plugin.msg(sender, "&e/admin &8- &7Menu GUI (en jeu)");
        plugin.msg(sender, "&e/draftmc reload");
        plugin.msg(sender, "&7Staff: &e/staff /sc /freeze /cps /reports /mute /tempban /ban /unban");
        plugin.msg(sender, "&7Monde: &e/hub set /portal /setwarp /outpost set /clearlag now");
        plugin.msg(sender, "&7Modo: &e/banitem /tags create /deathban");
        plugin.msg(sender, "&7Eco: &e/tokens give /money give /voteparty");
        plugin.msg(sender, "&7Fac: &e/f top add|remove|reset");
        plugin.msg(sender, "&7Events: &e/event help /totem /koth /teamfight /br ...");
        plugin.msg(sender, "&7Tournoi: &e/tournament help");
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiHolder)) {
            return;
        }
        GuiHolder gui = (GuiHolder) holder;
        if (!gui.menu().startsWith("admin-")) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        if (!canOpen(player)) {
            player.closeInventory();
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if ("admin-main".equals(gui.menu())) {
            List<String> cats = visibleCategories(player);
            for (int i = 0; i < cats.size() && i < SLOTS.length; i++) {
                if (slot == SLOTS[i]) {
                    openCategory(player, cats.get(i));
                    return;
                }
            }
            return;
        }
        if (slot == 45) {
            openMain(player);
            return;
        }
        if (current.getType() != Material.PAPER || !current.hasItemMeta() || !current.getItemMeta().hasDisplayName()) {
            return;
        }
        String clicked = CC.strip(current.getItemMeta().getDisplayName());
        ConfigurationSection sec = section(gui.extra());
        if (sec == null) {
            return;
        }
        for (String line : visibleCommands(player, sec)) {
            String[] parts = split(line);
            if (!parts[0].equalsIgnoreCase(clicked)) {
                continue;
            }
            String run = parts.length > 2 ? parts[2] : "";
            player.closeInventory();
            if (run.isEmpty()) {
                plugin.msg(player, "&eUsage &7» &f" + parts[0]);
            } else {
                player.performCommand(run);
            }
            return;
        }
    }

    private List<String> visibleCategories(Player player) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("admin-gui.categories");
        List<String> ids = new ArrayList<String>();
        if (root == null) {
            return Arrays.asList("staff", "plugin", "monde", "moderation", "economie", "factions", "events", "tournament");
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec != null && canSeeCategory(player, sec)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private List<String> visibleCommands(Player player, ConfigurationSection sec) {
        List<String> out = new ArrayList<String>();
        for (String line : sec.getStringList("commands")) {
            String[] parts = split(line);
            if (parts.length > 3 && !parts[3].isEmpty() && !player.hasPermission(parts[3])
                    && !player.hasPermission("draftmc.admin")) {
                continue;
            }
            out.add(line);
        }
        return out;
    }

    private boolean canSeeCategory(Player player, ConfigurationSection sec) {
        if (player.hasPermission("draftmc.admin")) {
            return true;
        }
        String perm = sec.getString("permission", "draftmc.staff");
        return perm.isEmpty() || player.hasPermission(perm);
    }

    private ConfigurationSection section(String id) {
        return plugin.getConfig().getConfigurationSection("admin-gui.categories." + id);
    }

    private String[] split(String line) {
        String[] parts = line.split("\\|", 4);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    private List<String> loreOr(ConfigurationSection sec, String fallback) {
        List<String> lore = sec.getStringList("lore");
        if (lore == null || lore.isEmpty()) {
            return Arrays.asList(fallback);
        }
        return lore;
    }

    private Material material(String raw) {
        try {
            return Material.valueOf(raw.toUpperCase());
        } catch (Exception e) {
            return Material.REDSTONE;
        }
    }
}
