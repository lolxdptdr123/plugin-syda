package fr.draftmc.playtime;

import fr.draftmc.Draftmc;
import fr.draftmc.gui.GuiHolder;
import fr.draftmc.gui.Menus;
import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlaytimeCommand implements CommandExecutor, Listener {
    private final Draftmc plugin;

    public PlaytimeCommand(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        open((Player) sender);
        return true;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new GuiHolder("ptr"), 54, CC.color("&8Playtime Rewards"));
        Menus.fill(inv);
        int seconds = plugin.classement().playtimeSeconds(player.getUniqueId());
        inv.setItem(4, new ItemBuilder(Material.WATCH)
                .name("&6&lTon temps de jeu")
                .lore("&7" + format(seconds), "&7Clique un palier pour récupérer", "&7sa récompense.").build());
        ConfigurationSection tiers = plugin.getConfig().getConfigurationSection("playtime-rewards.tiers");
        int slot = 19;
        if (tiers != null) {
            for (String id : tiers.getKeys(false)) {
                ConfigurationSection tier = tiers.getConfigurationSection(id);
                if (tier == null) {
                    continue;
                }
                int need = tier.getInt("seconds", 3600);
                boolean done = claimed(player.getUniqueId()).contains(id);
                boolean ready = seconds >= need && !done;
                short data = (short) (done ? 5 : (ready ? 4 : 14));
                List<String> lore = new ArrayList<String>();
                lore.add("&7Requis : &e" + format(need));
                lore.add("&7Récompense :");
                for (String line : tier.getStringList("lore")) {
                    lore.add("&7" + line);
                }
                lore.add("");
                if (done) {
                    lore.add("&aDéjà récupéré");
                } else if (ready) {
                    lore.add("&eClique pour récupérer !");
                } else {
                    int left = need - seconds;
                    lore.add("&cEncore &e" + format(left));
                }
                inv.setItem(slot, new ItemBuilder(Material.STAINED_GLASS_PANE, 1, data)
                        .name(tier.getString("name", "&ePalier " + id))
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
        }
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiHolder) || !"ptr".equals(((GuiHolder) holder).menu())) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        if (event.getRawSlot() == 49) {
            player.closeInventory();
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() != Material.STAINED_GLASS_PANE || !current.hasItemMeta()) {
            return;
        }
        String clicked = CC.strip(current.getItemMeta().getDisplayName());
        ConfigurationSection tiers = plugin.getConfig().getConfigurationSection("playtime-rewards.tiers");
        if (tiers == null) {
            return;
        }
        for (String id : tiers.getKeys(false)) {
            ConfigurationSection tier = tiers.getConfigurationSection(id);
            if (tier == null) {
                continue;
            }
            if (!CC.strip(CC.color(tier.getString("name", id))).equalsIgnoreCase(clicked)) {
                continue;
            }
            claim(player, id, tier);
            open(player);
            return;
        }
    }

    private void claim(Player player, String id, ConfigurationSection tier) {
        UUID uuid = player.getUniqueId();
        List<String> claimed = claimed(uuid);
        if (claimed.contains(id)) {
            plugin.msg(player, "&cTu as déjà récupéré ce palier.");
            return;
        }
        int need = tier.getInt("seconds", 3600);
        if (plugin.classement().playtimeSeconds(uuid) < need) {
            plugin.msg(player, "&cTu n'as pas encore assez de temps de jeu.");
            return;
        }
        claimed.add(id);
        plugin.data().setList(uuid, "ptr_claimed", claimed);
        double money = tier.getDouble("money", 0);
        long tokens = tier.getLong("tokens", 0);
        if (money > 0) {
            plugin.economy().deposit(player, money);
        }
        if (tokens > 0) {
            plugin.tokens().add(uuid, tokens);
        }
        for (String cmd : tier.getStringList("commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
        }
        plugin.msg(player, "&aRécompense playtime récupérée : " + tier.getString("name", id));
    }

    private List<String> claimed(UUID uuid) {
        return plugin.data().getList(uuid, "ptr_claimed");
    }

    public static String format(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        if (h > 0) {
            return h + "h " + m + "m";
        }
        return m + "m";
    }
}
