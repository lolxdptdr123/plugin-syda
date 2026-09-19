package fr.draftmc.warps;

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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class WarpManager implements CommandExecutor, TabCompleter, Listener {
    private final Draftmc plugin;
    private final YamlFile file;

    public WarpManager(Draftmc plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "warps.yml");
    }

    private boolean isStaff(CommandSender sender) {
        return sender.hasPermission("draftmc.staff") || sender.hasPermission("draftmc.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ("setwarp".equals(name)) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cJoueur uniquement.");
                return true;
            }
            if (!isStaff(sender)) {
                plugin.msg(sender, "&cCommande staff uniquement.");
                return true;
            }
            setWarp((Player) sender, args);
            return true;
        }
        if ("delwarp".equals(name)) {
            if (!isStaff(sender)) {
                plugin.msg(sender, "&cCommande staff uniquement.");
                return true;
            }
            delWarp(sender, args);
            return true;
        }
        if ("warps".equals(name)) {
            if (sender instanceof Player) {
                openMain((Player) sender);
            } else {
                list(sender);
            }
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 1) {
            openMain(player);
            return true;
        }
        goWarp(player, args);
        return true;
    }

    private void setWarp(Player player, String[] args) {
        if (args.length < 1) {
            plugin.msg(player, "&e/setwarp <nom> [categorie]");
            plugin.msg(player, "&7Catégories: &e" + join(categoryIds()));
            return;
        }
        String id = warpName(args[0]);
        if (id.isEmpty()) {
            plugin.msg(player, "&cNom invalide.");
            return;
        }
        String category = args.length >= 2
                ? args[1].toLowerCase(Locale.ROOT)
                : plugin.getConfig().getString("warps.default-category", "autres");
        if (!categoryIds().contains(category)) {
            plugin.msg(player, "&cCatégorie inconnue. &7" + join(categoryIds()));
            return;
        }
        file.get().set("warps." + id, null);
        file.get().set("warps." + id + ".location", Locations.serialize(player.getLocation()));
        file.get().set("warps." + id + ".category", category);
        file.save();
        plugin.msg(player, "&aWarp &e" + id + " &adéfini &7(" + category + "&7).");
    }

    private void delWarp(CommandSender sender, String[] args) {
        if (args.length < 1) {
            plugin.msg(sender, "&e/delwarp <nom>");
            return;
        }
        String id = warpName(args[0]);
        if (locationOf(id) == null) {
            plugin.msg(sender, "&cWarp introuvable.");
            return;
        }
        file.get().set("warps." + id, null);
        file.save();
        plugin.msg(sender, "&cWarp &e" + id + " &csupprimé.");
    }

    private void list(CommandSender sender) {
        List<String> names = warpNames();
        plugin.msg(sender, "&6Warps &7» &f" + names.size());
        if (names.isEmpty()) {
            plugin.msg(sender, "&7Aucun warp. &e/setwarp <nom>");
            return;
        }
        plugin.msg(sender, "&e" + join(names));
    }

    private void goWarp(Player player, String[] args) {
        teleportTo(player, warpName(args[0]));
    }

    public void teleportTo(Player player, String rawId) {
        final String id = warpName(rawId);
        final Location loc = locationOf(id);
        if (loc == null) {
            plugin.msg(player, "&cWarp &e" + id + " &cintrouvable. &e/warps");
            return;
        }
        plugin.teleports().request(player, loc, "&aTéléporté au warp &e" + id + "&a.");
    }

    public Location location(String name) {
        return locationOf(warpName(name));
    }

    private Location locationOf(String id) {
        ConfigurationSection section = file.get().getConfigurationSection("warps." + id);
        if (section != null) {
            return Locations.deserialize(section.getString("location"));
        }
        return Locations.deserialize(file.get().getString("warps." + id));
    }

    private List<String> warpNames() {
        List<String> names = new ArrayList<String>();
        ConfigurationSection section = file.get().getConfigurationSection("warps");
        if (section == null) {
            return names;
        }
        names.addAll(section.getKeys(false));
        Collections.sort(names);
        return names;
    }

    private String warpName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }

    private String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append("&7, &e");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(new GuiHolder("warps"), 54,
                CC.color(plugin.getConfig().getString("warps.gui.title", "&8Warps")));
        Menus.fill(inv);
        for (String category : categoryIds()) {
            ConfigurationSection sec = plugin.getConfig().getConfigurationSection("warps.gui.categories." + category);
            int slot = categorySlot(category, sec);
            Material mat = Material.matchMaterial(sec == null ? defaultMaterial(category) : sec.getString("material", defaultMaterial(category)));
            if (mat == null) {
                mat = Material.COMPASS;
            }
            int count = warpsIn(category).size();
            String name = sec == null ? "&e" + category : sec.getString("name", "&e" + category);
            List<String> lore = sec == null || sec.getStringList("lore").isEmpty()
                    ? java.util.Arrays.asList("&7" + count + " warp(s)", "&eClique pour ouvrir.")
                    : withCount(sec.getStringList("lore"), count);
            inv.setItem(slot, new ItemBuilder(mat)
                    .name(name)
                    .lore(lore)
                    .build());
        }
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
    }

    private void openCategory(Player player, String category) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("warps.gui.categories." + category);
        String title = sec == null ? "&8Warps" : sec.getString("title", sec.getString("name", "&8Warps"));
        Inventory inv = Bukkit.createInventory(new GuiHolder("warps-cat", category), 54, CC.color(title));
        Menus.fill(inv);
        List<String> names = warpsIn(category);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < names.size() && i < slots.length; i++) {
            String id = names.get(i);
            inv.setItem(slots[i], new ItemBuilder(Material.ENDER_PEARL)
                    .name("&e" + id)
                    .lore("&7Clique pour te téléporter.").build());
        }
        if (names.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("&cAucun warp")
                    .lore("&7Staff: &e/setwarp <nom> " + category).build());
        }
        inv.setItem(45, Menus.back());
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof GuiHolder)) {
            return;
        }
        GuiHolder gui = (GuiHolder) holder;
        if (!"warps".equals(gui.menu()) && !"warps-cat".equals(gui.menu())) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        if ("warps".equals(gui.menu())) {
            for (String category : categoryIds()) {
                if (isCategoryClick(event.getRawSlot(), clicked, category)) {
                    openCategory(player, category);
                    return;
                }
            }
            return;
        }
        if (event.getRawSlot() == 45) {
            openMain(player);
            return;
        }
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.STAINED_GLASS_PANE) {
            return;
        }
        if (clicked.getType() == Material.BARRIER) {
            return;
        }
        if (!clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) {
            return;
        }
        String id = warpName(CC.strip(clicked.getItemMeta().getDisplayName()));
        player.closeInventory();
        teleportTo(player, id);
    }

    private boolean isCategoryClick(int slot, ItemStack clicked, String category) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("warps.gui.categories." + category);
        if (slot == categorySlot(category, sec)) {
            return true;
        }
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) {
            return false;
        }
        String expected = CC.strip(CC.color(sec == null ? category : sec.getString("name", category)));
        return expected.equalsIgnoreCase(CC.strip(clicked.getItemMeta().getDisplayName()));
    }

    private int categorySlot(String category, ConfigurationSection sec) {
        if (sec != null && sec.contains("slot")) {
            return sec.getInt("slot");
        }
        if ("events".equals(category)) {
            return 20;
        }
        if ("utilitaires".equals(category)) {
            return 22;
        }
        if ("pvp".equals(category)) {
            return 24;
        }
        if ("autres".equals(category)) {
            return 31;
        }
        return 22;
    }

    private String defaultMaterial(String category) {
        if ("events".equals(category)) {
            return "DIAMOND_SWORD";
        }
        if ("pvp".equals(category)) {
            return "IRON_SWORD";
        }
        if ("autres".equals(category)) {
            return "CHEST";
        }
        return "COMPASS";
    }

    private List<String> categoryIds() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("warps.gui.categories");
        List<String> ids = new ArrayList<String>();
        if (sec == null) {
            ids.add("events");
            ids.add("utilitaires");
            ids.add("pvp");
            ids.add("autres");
            return ids;
        }
        ids.addAll(sec.getKeys(false));
        return ids;
    }

    private List<String> warpsIn(String category) {
        List<String> out = new ArrayList<String>();
        for (String id : warpNames()) {
            if (categoryOf(id).equalsIgnoreCase(category)) {
                out.add(id);
            }
        }
        return out;
    }

    private String categoryOf(String id) {
        ConfigurationSection section = file.get().getConfigurationSection("warps." + id);
        if (section != null) {
            String cat = section.getString("category", "");
            if (cat != null && !cat.isEmpty()) {
                return cat.toLowerCase(Locale.ROOT);
            }
        }
        String mapped = plugin.getConfig().getString("warps.category-of." + id);
        if (mapped != null && !mapped.isEmpty()) {
            return mapped.toLowerCase(Locale.ROOT);
        }
        return plugin.getConfig().getString("warps.default-category", "autres");
    }

    private List<String> withCount(List<String> lore, int count) {
        List<String> out = new ArrayList<String>();
        for (String line : lore) {
            out.add(line.replace("{count}", String.valueOf(count)));
        }
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            if (!"warp".equals(name) && !"delwarp".equals(name) && !"setwarp".equals(name)) {
                return Collections.emptyList();
            }
            if (("delwarp".equals(name) || "setwarp".equals(name)) && !isStaff(sender)) {
                return Collections.emptyList();
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<String>();
            for (String warp : warpNames()) {
                if (warp.startsWith(prefix)) {
                    out.add(warp);
                }
            }
            return out;
        }
        if (args.length == 2 && "setwarp".equals(name) && isStaff(sender)) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<String>();
            for (String cat : categoryIds()) {
                if (cat.startsWith(prefix)) {
                    out.add(cat);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}
