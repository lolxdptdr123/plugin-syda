package fr.draftmc.tags;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import fr.draftmc.util.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TagManager implements CommandExecutor, TabCompleter, Listener {
    public static final String TITLE = CC.color("&8Tags");
    private final Draftmc plugin;
    private final YamlFile file;
    private final List<Tag> defaults = Arrays.asList(
            new Tag("nouveau", "&7Nouveau", 0, null, false),
            new Tag("killer", "&cKiller", 10, "players_killed", false),
            new Tag("assassin", "&4Assassin", 50, "players_killed", false),
            new Tag("farmer", "&aFarmer", 100, "crops_broken", false),
            new Tag("mineur", "&bMineur", 1000, "blocks_mined", false),
            new Tag("veteran", "&eVétéran", 36000, "playtime", false),
            new Tag("chasseur", "&2Chasseur", 100, "mobs_killed", false),
            new Tag("survivant", "&8Survivant", 20, "deaths", false),
            new Tag("vip", "&aVIP", 0, "perm:draftmc.tag.vip", false),
            new Tag("booster", "&dBooster", 0, "perm:draftmc.tag.booster", false)
    );
    private final List<Tag> tags = new ArrayList<Tag>();

    public TagManager(Draftmc plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "tags.yml");
        reload();
    }

    public void reload() {
        tags.clear();
        tags.addAll(defaults);
        ConfigurationSection custom = file.get().getConfigurationSection("custom");
        if (custom != null) {
            for (String id : custom.getKeys(false)) {
                String display = custom.getString(id + ".display", "&7" + id);
                String perm = custom.getString(id + ".permission", "");
                String stat = perm == null || perm.isEmpty() ? null : "perm:" + perm;
                Tag existing = byId(id);
                Tag created = new Tag(id.toLowerCase(Locale.ROOT), display, 0, stat, true);
                if (existing != null) {
                    tags.remove(existing);
                }
                tags.add(created);
            }
        }
    }

    public String display(Player player) {
        String id = plugin.data().getString(player.getUniqueId(), "tag");
        Tag tag = byId(id);
        return tag == null ? "" : CC.color(tag.display + " ");
    }

    public String selectedName(Player player) {
        String id = plugin.data().getString(player.getUniqueId(), "tag");
        Tag tag = byId(id);
        return tag == null ? "" : ChatColor.stripColor(CC.color(tag.display)).trim();
    }

    private Tag byId(String id) {
        if (id == null) {
            return null;
        }
        for (Tag t : tags) {
            if (t.id.equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    private boolean isStaff(CommandSender sender) {
        return sender.hasPermission("draftmc.staff") || sender.hasPermission("draftmc.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("create") || sub.equals("delete") || sub.equals("remove")
                    || sub.equals("list") || sub.equals("set") || sub.equals("give")) {
                if (!isStaff(sender)) {
                    plugin.msg(sender, "&cCommande staff uniquement.");
                    return true;
                }
                if (sub.equals("create")) {
                    create(sender, args);
                } else if (sub.equals("delete") || sub.equals("remove")) {
                    delete(sender, args);
                } else if (sub.equals("list")) {
                    list(sender);
                } else {
                    setTag(sender, args);
                }
                return true;
            }
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        open((Player) sender);
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.msg(sender, "&e/tags create <id> <display>");
            plugin.msg(sender, "&7Ex. &e/tags create youtube &cYouTube");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (id.isEmpty()) {
            plugin.msg(sender, "&cId invalide.");
            return;
        }
        if (byId(id) != null) {
            plugin.msg(sender, "&cCe tag existe déjà.");
            return;
        }
        String display = join(args, 2);
        file.get().set("custom." + id + ".display", display);
        file.save();
        tags.add(new Tag(id, display, 0, null, true));
        plugin.msg(sender, "&aTag créé : " + display + " &8(" + id + ")");
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.msg(sender, "&e/tags delete <id>");
            return;
        }
        Tag tag = byId(args[1]);
        if (tag == null) {
            plugin.msg(sender, "&cTag introuvable.");
            return;
        }
        if (!tag.custom) {
            plugin.msg(sender, "&cTu ne peux pas supprimer un tag par défaut.");
            return;
        }
        file.get().set("custom." + tag.id, null);
        file.save();
        tags.remove(tag);
        plugin.msg(sender, "&cTag &e" + tag.id + " &csupprimé.");
    }

    private void list(CommandSender sender) {
        plugin.msg(sender, "&6Tags &7(" + tags.size() + ")");
        for (Tag tag : tags) {
            plugin.msg(sender, (tag.custom ? "&a● " : "&8● ") + tag.display + " &8(" + tag.id + ")");
        }
    }

    private void setTag(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.msg(sender, "&e/tags set <joueur> <id>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            plugin.msg(sender, "&cJoueur introuvable.");
            return;
        }
        Tag tag = byId(args[2]);
        if (tag == null) {
            plugin.msg(sender, "&cTag introuvable.");
            return;
        }
        plugin.data().setString(target.getUniqueId(), "tag", tag.id);
        plugin.msg(sender, "&aTag de &e" + target.getName() + " &a» " + tag.display);
        plugin.msg(target, "&aTon tag a été défini : " + tag.display);
    }

    private String join(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) {
                sb.append(" ");
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    public void open(Player player) {
        int size = tags.size() <= 27 ? 27 : 54;
        Inventory inv = Bukkit.createInventory(null, size, TITLE);
        int slot = 0;
        for (Tag tag : tags) {
            if (slot >= size) {
                break;
            }
            boolean unlocked = unlocked(player, tag);
            ItemBuilder b = new ItemBuilder(unlocked ? Material.NAME_TAG : Material.BARRIER).name(tag.display);
            List<String> lore = new ArrayList<String>();
            lore.add(unlocked ? "&aDébloqué" : "&cVerrouillé");
            lore.add("&8" + tag.id);
            if (tag.stat != null && !tag.stat.startsWith("perm:")) {
                lore.add("&7Objectif: &e" + tag.need + " " + tag.stat);
                lore.add("&7Progression: &f" + plugin.data().getInt(player.getUniqueId(), tag.stat));
            }
            b.lore(lore);
            inv.setItem(slot, b.build());
            slot++;
        }
        player.openInventory(inv);
    }

    private boolean unlocked(Player player, Tag tag) {
        if (tag.stat == null) {
            return true;
        }
        if (tag.stat.startsWith("perm:")) {
            return player.hasPermission(tag.stat.substring(5));
        }
        return plugin.data().getInt(player.getUniqueId(), tag.stat) >= tag.need;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getCurrentItem() == null) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        Tag tag = null;
        for (Tag candidate : tags) {
            if (CC.color(candidate.display).equals(meta.getDisplayName())) {
                tag = candidate;
                break;
            }
        }
        if (tag == null) {
            return;
        }
        if (!unlocked(player, tag)) {
            plugin.msg(player, "&cTag non débloqué.");
            return;
        }
        plugin.data().setString(player.getUniqueId(), "tag", tag.id);
        plugin.msg(player, "&aTag sélectionné: " + tag.display);
        player.closeInventory();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String factionPrefix = plugin.factions().publicPrefix(player);
        String gradePrefix = plugin.grades().chatPrefix(player);
        String tagSuffix = display(player);
        event.setFormat(CC.color("&7" + factionPrefix + gradePrefix + "%1$s " + tagSuffix + "&8» &f%2$s"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!isStaff(sender)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(Arrays.asList("create", "delete", "list", "set"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("set"))) {
            if (args[0].equalsIgnoreCase("set")) {
                return null;
            }
            List<String> ids = new ArrayList<String>();
            for (Tag tag : tags) {
                if (tag.custom) {
                    ids.add(tag.id);
                }
            }
            return filter(ids, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            List<String> ids = new ArrayList<String>();
            for (Tag tag : tags) {
                ids.add(tag.id);
            }
            return filter(ids, args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String token) {
        String prefix = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }

    public static class Tag {
        public final String id;
        public final String display;
        public final int need;
        public final String stat;
        public final boolean custom;

        public Tag(String id, String display, int need, String stat, boolean custom) {
            this.id = id;
            this.display = display;
            this.need = need;
            this.stat = stat;
            this.custom = custom;
        }
    }
}
