package fr.draftmc.hdv;

import fr.draftmc.Draftmc;
import fr.draftmc.util.CC;
import fr.draftmc.util.ItemBuilder;
import fr.draftmc.util.Items;
import fr.draftmc.util.YamlFile;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class AuctionManager implements CommandExecutor, Listener {
    private final Draftmc plugin;
    private final YamlFile file;

    public AuctionManager(Draftmc plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "hdv.yml");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length >= 1 && ("sell".equalsIgnoreCase(args[0]) || "vendre".equalsIgnoreCase(args[0]))) {
            sell(player, args);
            return true;
        }
        open(player, 0);
        return true;
    }

    private void sell(Player player, String[] args) {
        int max = plugin.grades().perks().maxHdv(player);
        if (max <= 0) {
            plugin.msg(player, "&cTon grade n'a pas accès à l'HDV. &7Grade &eChevalier &7minimum.");
            return;
        }
        if (countListings(player.getUniqueId()) >= max) {
            plugin.msg(player, "&cTu as déjà &e" + max + " &cventes. &7Attends qu'elles se vendent ou retire-les.");
            return;
        }
        if (args.length < 2) {
            plugin.msg(player, "&e/hdv sell <prix>");
            return;
        }
        double price;
        try {
            price = Double.parseDouble(args[1].replace(",", "."));
        } catch (NumberFormatException ex) {
            plugin.msg(player, "&cPrix invalide.");
            return;
        }
        if (price < 1) {
            plugin.msg(player, "&cPrix minimum: &e1");
            return;
        }
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR) {
            plugin.msg(player, "&cPrends l'item à vendre en main.");
            return;
        }
        String id = UUID.randomUUID().toString();
        String path = "listings." + id;
        file.get().set(path + ".seller", player.getUniqueId().toString());
        file.get().set(path + ".seller-name", player.getName());
        file.get().set(path + ".price", price);
        file.get().set(path + ".item", Items.toBase64(hand));
        file.get().set(path + ".time", System.currentTimeMillis());
        file.save();
        player.setItemInHand(null);
        plugin.msg(player, "&aItem mis en vente pour &e" + plugin.economy().format(price) + "&a. &7/hdv");
    }

    public void open(Player player, int page) {
        List<String> ids = listingIds();
        int perPage = 45;
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) perPage));
        page = Math.max(0, Math.min(page, pages - 1));
        GuiHolder holder = new GuiHolder(page);
        Inventory inv = Bukkit.createInventory(holder, 54, CC.color("&8HDV &7» &eVentes"));
        holder.inventory = inv;
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < ids.size(); i++) {
            String id = ids.get(start + i);
            inv.setItem(i, listingIcon(player, id));
            holder.slotToId[i] = id;
        }
        int max = plugin.grades().perks().maxHdv(player);
        inv.setItem(49, new ItemBuilder(Material.EMERALD)
                .name("&aTes ventes: &e" + countListings(player.getUniqueId()) + "&7/&e" + max)
                .lore("&7/hdv sell <prix> &8pour vendre l'item en main.",
                        "&eClic gauche &7= acheter",
                        "&cClic droit &7= retirer ta vente")
                .build());
        if (page > 0) {
            inv.setItem(45, new ItemBuilder(Material.ARROW).name("&ePage précédente").build());
        }
        if (page + 1 < pages) {
            inv.setItem(53, new ItemBuilder(Material.ARROW).name("&ePage suivante").build());
        }
        player.openInventory(inv);
    }

    private ItemStack listingIcon(Player viewer, String id) {
        String path = "listings." + id;
        ItemStack item = Items.fromBase64(file.get().getString(path + ".item"));
        if (item == null) {
            return new ItemBuilder(Material.BARRIER).name("&cItem corrompu").build();
        }
        double price = file.get().getDouble(path + ".price");
        String seller = file.get().getString(path + ".seller-name", "?");
        ItemBuilder builder = new ItemBuilder(item);
        builder.addLore("");
        builder.addLore("&7Vendeur: &e" + seller);
        builder.addLore("&7Prix: &a" + plugin.economy().format(price));
        UUID sellerId = UUID.fromString(file.get().getString(path + ".seller"));
        if (sellerId.equals(viewer.getUniqueId())) {
            builder.addLore("&cClic droit pour retirer");
        } else {
            builder.addLore("&eClic gauche pour acheter");
        }
        return builder.build();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        GuiHolder holder = (GuiHolder) event.getView().getTopInventory().getHolder();
        int slot = event.getRawSlot();
        if (slot == 45 && holder.page > 0) {
            open(player, holder.page - 1);
            return;
        }
        if (slot == 53) {
            open(player, holder.page + 1);
            return;
        }
        if (slot < 0 || slot >= 45) {
            return;
        }
        String id = holder.slotToId[slot];
        if (id == null || !file.get().contains("listings." + id)) {
            return;
        }
        UUID seller = UUID.fromString(file.get().getString("listings." + id + ".seller"));
        if (event.isRightClick() && seller.equals(player.getUniqueId())) {
            cancel(player, id);
            open(player, holder.page);
            return;
        }
        if (event.isLeftClick() && !seller.equals(player.getUniqueId())) {
            buy(player, id);
            open(player, holder.page);
        }
    }

    private void buy(Player player, String id) {
        String path = "listings." + id;
        if (!file.get().contains(path)) {
            return;
        }
        double price = file.get().getDouble(path + ".price");
        UUID seller = UUID.fromString(file.get().getString(path + ".seller"));
        ItemStack item = Items.fromBase64(file.get().getString(path + ".item"));
        if (item == null) {
            plugin.msg(player, "&cItem corrompu.");
            return;
        }
        if (!plugin.economy().withdraw(player, price)) {
            plugin.msg(player, "&cPas assez d'argent. &7Prix: &a" + plugin.economy().format(price));
            return;
        }
        plugin.economy().deposit(seller, price);
        file.get().set(path, null);
        file.save();
        player.getInventory().addItem(item);
        plugin.msg(player, "&aAchat HDV effectué pour &e" + plugin.economy().format(price) + "&a.");
        Player sellerPlayer = Bukkit.getPlayer(seller);
        if (sellerPlayer != null) {
            plugin.msg(sellerPlayer, "&aUn item HDV a été vendu pour &e" + plugin.economy().format(price) + "&a.");
        }
    }

    private void cancel(Player player, String id) {
        String path = "listings." + id;
        ItemStack item = Items.fromBase64(file.get().getString(path + ".item"));
        file.get().set(path, null);
        file.save();
        if (item != null) {
            player.getInventory().addItem(item);
        }
        plugin.msg(player, "&eVente retirée, item rendu.");
    }

    private int countListings(UUID uuid) {
        int count = 0;
        for (String id : listingIds()) {
            if (uuid.toString().equals(file.get().getString("listings." + id + ".seller"))) {
                count++;
            }
        }
        return count;
    }

    private List<String> listingIds() {
        if (!file.get().isConfigurationSection("listings")) {
            return new ArrayList<String>();
        }
        Set<String> keys = file.get().getConfigurationSection("listings").getKeys(false);
        return new ArrayList<String>(keys);
    }

    private static class GuiHolder implements InventoryHolder {
        private final int page;
        private final String[] slotToId = new String[45];
        private Inventory inventory;

        private GuiHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
