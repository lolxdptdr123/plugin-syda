package fr.draftmc.events.totem;

import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class TotemStatsCommand implements CommandExecutor, Listener {
    private static final int PER_PAGE = 45;
    private final TotemPlugin plugin;

    public TotemStatsCommand(TotemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(CC.color("&cCommande joueur uniquement."));
            return true;
        }
        open((Player) sender, 0);
        return true;
    }

    public void open(Player player, int page) {
        TotemManager manager = plugin.getTotemManager();
        if (!manager.hasStats()) {
            player.sendMessage(plugin.prefix() + CC.color("&cAucune stat Totem (expirees ou pas d'event recent)."));
            return;
        }
        List<TotemManager.TotemStat> stats = manager.getLastStats();
        int pages = Math.max(1, (stats.size() + PER_PAGE - 1) / PER_PAGE);
        if (page < 0) {
            page = 0;
        }
        if (page >= pages) {
            page = pages - 1;
        }
        Inventory inv = Bukkit.createInventory(new Holder(page), 54, CC.color("&8Totem Stats"));
        int start = page * PER_PAGE;
        int end = Math.min(stats.size(), start + PER_PAGE);
        int slot = 0;
        for (int i = start; i < end; i++) {
            TotemManager.TotemStat stat = stats.get(i);
            inv.setItem(slot++, head(i + 1, stat));
        }
        if (page > 0) {
            inv.setItem(45, new ItemBuilder(Material.ARROW).name("&ePage precedente").build());
        }
        inv.setItem(49, new ItemBuilder(Material.PAPER)
                .name("&6Page &e" + (page + 1) + "&7/&e" + pages)
                .lore("&7Blocs casses pendant le Totem",
                        "&7Temps restant : &e" + manager.statsSecondsLeft() + "s")
                .build());
        if (page + 1 < pages) {
            inv.setItem(53, new ItemBuilder(Material.ARROW).name("&ePage suivante").build());
        }
        player.openInventory(inv);
    }

    private ItemStack head(int rank, TotemManager.TotemStat stat) {
        ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwner(stat.name);
        meta.setDisplayName(CC.color("&e#" + rank + " &f" + stat.name));
        java.util.List<String> lore = new java.util.ArrayList<String>();
        lore.add(CC.color("&7Blocs casses : &6" + stat.blocks));
        meta.setLore(lore);
        skull.setItemMeta(meta);
        return skull;
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
        if (slot == 45 && holder.page > 0) {
            open(player, holder.page - 1);
        } else if (slot == 53) {
            open(player, holder.page + 1);
        }
    }

    static class Holder implements InventoryHolder {
        final int page;

        Holder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
