package fr.draftmc.collection;

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

public class CollectionManager implements CommandExecutor, Listener {
    private final Draftmc plugin;

    public CollectionManager(Draftmc plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        openMain((Player) sender);
        return true;
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(new GuiHolder("col-main"), 45, CC.color("&8Collections"));
        Menus.fill(inv);
        ConfigurationSection crates = plugin.getConfig().getConfigurationSection("collections.crates");
        int slot = 19;
        if (crates != null) {
            for (String id : crates.getKeys(false)) {
                ConfigurationSection crate = crates.getConfigurationSection(id);
                if (crate == null) {
                    continue;
                }
                int total = 0;
                int found = 0;
                ConfigurationSection items = crate.getConfigurationSection("items");
                if (items != null) {
                    for (String itemId : items.getKeys(false)) {
                        total++;
                        if (hasItem(player.getUniqueId(), id, itemId)) {
                            found++;
                        }
                    }
                }
                inv.setItem(slot, new ItemBuilder(material(crate.getString("material", "CHEST")))
                        .name(crate.getString("name", "&e" + id))
                        .lore("&7Progression : &e" + found + "&7/&e" + total,
                                "&eClique pour voir les items.")
                        .build());
                slot += 2;
            }
        }
        inv.setItem(40, Menus.close());
        player.openInventory(inv);
    }

    public void openCrate(Player player, String crateId) {
        ConfigurationSection crate = plugin.getConfig().getConfigurationSection("collections.crates." + crateId);
        if (crate == null) {
            openMain(player);
            return;
        }
        Inventory inv = Bukkit.createInventory(new GuiHolder("col-crate", crateId), 54,
                CC.color(crate.getString("name", "&8Collection")));
        Menus.fill(inv);
        ConfigurationSection items = crate.getConfigurationSection("items");
        int slot = 10;
        if (items != null) {
            for (String itemId : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(itemId);
                if (item == null) {
                    continue;
                }
                boolean owned = hasItem(player.getUniqueId(), crateId, itemId);
                List<String> lore = new ArrayList<String>(item.getStringList("lore"));
                lore.add("");
                lore.add(owned ? "&aObtenu" : "&cPas encore obtenu");
                Material mat = owned ? material(item.getString("material", "CHEST")) : Material.STAINED_GLASS_PANE;
                short data = (short) (owned ? 0 : 15);
                ItemBuilder builder = owned
                        ? new ItemBuilder(mat)
                        : new ItemBuilder(mat, 1, data);
                inv.setItem(slot, builder
                        .name(owned ? item.getString("name", itemId) : "&8???")
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
        inv.setItem(45, Menus.back());
        inv.setItem(49, Menus.close());
        player.openInventory(inv);
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
        if (!gui.menu().startsWith("col-")) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot == 40 || slot == 49) {
            player.closeInventory();
            return;
        }
        if ("col-crate".equals(gui.menu()) && slot == 45) {
            openMain(player);
            return;
        }
        if ("col-main".equals(gui.menu())) {
            ConfigurationSection crates = plugin.getConfig().getConfigurationSection("collections.crates");
            if (crates == null) {
                return;
            }
            int index = 19;
            for (String id : crates.getKeys(false)) {
                if (slot == index) {
                    openCrate(player, id);
                    return;
                }
                index += 2;
            }
        }
    }

    @EventHandler
    public void onPickup(org.bukkit.event.player.PlayerPickupItemEvent event) {
        discover(event.getPlayer(), event.getItem().getItemStack());
    }

    public void discover(Player player, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || !stack.getItemMeta().hasDisplayName()) {
            return;
        }
        String name = CC.strip(stack.getItemMeta().getDisplayName());
        ConfigurationSection crates = plugin.getConfig().getConfigurationSection("collections.crates");
        if (crates == null) {
            return;
        }
        for (String crateId : crates.getKeys(false)) {
            ConfigurationSection items = crates.getConfigurationSection(crateId + ".items");
            if (items == null) {
                continue;
            }
            for (String itemId : items.getKeys(false)) {
                String itemName = items.getString(itemId + ".name", "");
                if (CC.strip(CC.color(itemName)).equalsIgnoreCase(name)) {
                    String key = crateId + ":" + itemId;
                    List<String> found = plugin.data().getList(player.getUniqueId(), "collections");
                    if (!found.contains(key)) {
                        found.add(key);
                        plugin.data().setList(player.getUniqueId(), "collections", found);
                        plugin.msg(player, "&aCollection : &e" + itemName + " &7ajouté !");
                    }
                }
            }
        }
    }

    private boolean hasItem(UUID uuid, String crate, String item) {
        return plugin.data().getList(uuid, "collections").contains(crate + ":" + item);
    }

    private Material material(String raw) {
        try {
            return Material.valueOf(raw.toUpperCase());
        } catch (Exception e) {
            return Material.CHEST;
        }
    }
}
