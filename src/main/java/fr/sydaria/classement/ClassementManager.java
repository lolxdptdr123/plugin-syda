package fr.sydaria.classement;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import fr.sydaria.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ClassementManager implements Listener, CommandExecutor {
    public static final String TITLE = CC.color("&8Classements");
    private final Sydaria plugin;
    private final Map<UUID, Long> joinedAt = new HashMap<UUID, Long>();

    public ClassementManager(Sydaria plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.data().ensure(event.getPlayer());
        joinedAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        flushPlaytime(event.getPlayer().getUniqueId());
    }

    public void flushPlaytime(UUID uuid) {
        Long start = joinedAt.remove(uuid);
        if (start == null) {
            return;
        }
        int seconds = (int) ((System.currentTimeMillis() - start) / 1000L);
        plugin.data().addInt(uuid, "playtime", seconds);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        plugin.data().addInt(event.getPlayer().getUniqueId(), "blocks_mined", 1);
        Material type = event.getBlock().getType();
        if (isCrop(type)) {
            plugin.data().addInt(event.getPlayer().getUniqueId(), "crops_broken", 1);
        }
    }

    private boolean isCrop(Material type) {
        return type == Material.CROPS
                || type == Material.CARROT
                || type == Material.POTATO
                || type == Material.SUGAR_CANE_BLOCK
                || type == Material.MELON_BLOCK
                || type == Material.PUMPKIN
                || type == Material.NETHER_WARTS
                || type == Material.COCOA;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            return;
        }
        if (event.getEntityType() != EntityType.ARMOR_STAND) {
            plugin.data().addInt(event.getEntity().getKiller().getUniqueId(), "mobs_killed", 1);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        plugin.data().addInt(event.getEntity().getUniqueId(), "deaths", 1);
        if (event.getEntity().getKiller() != null) {
            plugin.data().addInt(event.getEntity().getKiller().getUniqueId(), "players_killed", 1);
        }
    }

    public void addQuest(Player player) {
        plugin.data().addInt(player.getUniqueId(), "quests", 1);
    }

    public void addEventHit(Player player) {
        plugin.data().addInt(player.getUniqueId(), "event_hits", 1);
    }

    public void addTotemBlock(Player player) {
        plugin.data().addInt(player.getUniqueId(), "totem_blocks", 1);
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
        inv.setItem(10, icon(Material.DIAMOND_PICKAXE, "&bBlocs minés", "blocks_mined"));
        inv.setItem(11, icon(Material.BOOK, "&eQuêtes", "quests"));
        inv.setItem(12, icon(Material.WATCH, "&aTemps de jeu", "playtime"));
        inv.setItem(13, icon(Material.WHEAT, "&2Cultures", "crops_broken"));
        inv.setItem(14, icon(Material.ROTTEN_FLESH, "&6Mobs tués", "mobs_killed"));
        inv.setItem(15, icon(Material.DIAMOND_SWORD, "&cJoueurs tués", "players_killed"));
        inv.setItem(16, icon(Material.SKULL_ITEM, "&8Morts", "deaths"));
        inv.setItem(21, icon(Material.IRON_SWORD, "&dHits events", "event_hits"));
        inv.setItem(23, icon(Material.BEACON, "&6Blocs totem", "totem_blocks"));
        player.openInventory(inv);
    }

    private org.bukkit.inventory.ItemStack icon(Material mat, String name, String key) {
        ItemBuilder b = new ItemBuilder(mat).name(name);
        List<Entry> top = top(key, 10);
        if (top.isEmpty()) {
            b.lore("&7Aucune donnée.");
        } else {
            List<String> lore = new ArrayList<String>();
            int i = 1;
            for (Entry e : top) {
                lore.add("&e#" + i + " &f" + e.name + " &7» &6" + format(key, e.value));
                i++;
            }
            b.lore(lore);
        }
        return b.build();
    }

    private String format(String key, int value) {
        if ("playtime".equals(key)) {
            int h = value / 3600;
            int m = (value % 3600) / 60;
            return h + "h " + m + "m";
        }
        return String.valueOf(value);
    }

    public List<Entry> top(String key, int limit) {
        List<Entry> list = new ArrayList<Entry>();
        ConfigurationSection section = plugin.data().section();
        if (section == null) {
            return list;
        }
        for (String id : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                int value = plugin.data().getInt(uuid, key);
                if (value <= 0) {
                    continue;
                }
                list.add(new Entry(plugin.data().nameOf(uuid), value));
            } catch (Exception ignored) {
            }
        }
        Collections.sort(list, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Integer.compare(b.value, a.value);
            }
        });
        if (list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    public static class Entry {
        public final String name;
        public final int value;

        public Entry(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}
