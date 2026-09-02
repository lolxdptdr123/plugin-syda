package fr.sydaria.tags;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import fr.sydaria.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TagManager implements CommandExecutor, Listener {
    public static final String TITLE = CC.color("&8Tags");
    private final Sydaria plugin;
    private final List<Tag> tags = Arrays.asList(
            new Tag("nouveau", "&7Nouveau", 0, null),
            new Tag("killer", "&cKiller", 10, "players_killed"),
            new Tag("assassin", "&4Assassin", 50, "players_killed"),
            new Tag("farmer", "&aFarmer", 100, "crops_broken"),
            new Tag("mineur", "&bMineur", 1000, "blocks_mined"),
            new Tag("champion", "&6Champion", 25, "totem_blocks"),
            new Tag("veteran", "&eVétéran", 36000, "playtime"),
            new Tag("chasseur", "&2Chasseur", 100, "mobs_killed"),
            new Tag("survivant", "&8Survivant", 20, "deaths"),
            new Tag("eventeur", "&dEventeur", 100, "event_hits"),
            new Tag("vip", "&aVIP", 0, "perm:sydaria.tag.vip"),
            new Tag("booster", "&dBooster", 0, "perm:sydaria.tag.booster")
    );

    public TagManager(Sydaria plugin) {
        this.plugin = plugin;
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        open((Player) sender);
        return true;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        int slot = 10;
        for (Tag tag : tags) {
            boolean unlocked = unlocked(player, tag);
            ItemBuilder b = new ItemBuilder(unlocked ? Material.NAME_TAG : Material.BARRIER).name(tag.display);
            List<String> lore = new ArrayList<String>();
            lore.add(unlocked ? "&aDébloqué" : "&cVerrouillé");
            if (tag.stat != null && !tag.stat.startsWith("perm:")) {
                lore.add("&7Objectif: &e" + tag.need + " " + tag.stat);
                lore.add("&7Progression: &f" + plugin.data().getInt(player.getUniqueId(), tag.stat));
            }
            b.lore(lore);
            inv.setItem(slot, b.build());
            slot++;
            if (slot == 17) {
                slot = 19;
            }
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
        int index = event.getRawSlot() >= 19 ? event.getRawSlot() - 12 : event.getRawSlot() - 10;
        if (index < 0 || index >= tags.size()) {
            return;
        }
        Tag tag = tags.get(index);
        if (!unlocked(player, tag)) {
            plugin.msg(player, "&cTag non débloqué.");
            return;
        }
        plugin.data().setString(player.getUniqueId(), "tag", tag.id);
        plugin.msg(player, "&aTag sélectionné: " + tag.display);
        player.closeInventory();
    }

    /**
     * Compose le format complet du chat public : préfixe de faction, puis préfixe de
     * grade (LuckPerms/PEX, voir GradeManager — donne des perms/commandes en plus),
     * puis le pseudo, puis le tag choisi (voir TagManager.tags — purement cosmétique,
     * ne donne ni permission ni commande), puis le message.
     *
     * C'est volontairement le SEUL endroit qui appelle event.setFormat() pour le chat
     * public : avant ce correctif, ce handler écrasait entièrement le format, ce qui
     * effaçait le préfixe de faction posé par un autre listener. En centralisant ici,
     * chaque brique (faction / grade / tag) reste indépendante et rien ne s'efface.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String factionPrefix = plugin.factions().publicPrefix(player);
        String gradePrefix = plugin.grades().chatPrefix(player);
        String tagSuffix = display(player);
        event.setFormat(CC.color("&7" + factionPrefix + gradePrefix + "%1$s " + tagSuffix + "&8» &f%2$s"));
    }

    public static class Tag {
        public final String id;
        public final String display;
        public final int need;
        public final String stat;

        public Tag(String id, String display, int need, String stat) {
            this.id = id;
            this.display = display;
            this.need = need;
            this.stat = stat;
        }
    }
}
