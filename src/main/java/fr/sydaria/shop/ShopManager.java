package fr.sydaria.shop;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import fr.sydaria.util.ItemBuilder;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * GUI boutique/shop.
 *
 * Compatible avec deux formats de configuration :
 * 1) categories.<id>.items = [ITEM_A, ITEM_B] + shop/boutique.items.<id>
 * 2) categories.<id>.items.<id> = { ... } (définition directement dans la catégorie)
 *
 * Les slots, boutons et pagination sont configurables dans config.yml.
 */
public class ShopManager implements Listener, CommandExecutor {
    private final Sydaria plugin;

    public ShopManager(Sydaria plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        boolean money = isShopCommand(command, label);
        String root = money ? "shop" : "boutique";
        if (!plugin.getConfig().getBoolean(root + ".enabled", true)) {
            plugin.msg(player, money
                    ? "&cLe shop (argent) est désactivé."
                    : "&cLa boutique (tokens) est désactivée.");
            return true;
        }
        openMain(player, money, 0);
        return true;
    }

    private boolean isShopCommand(Command command, String label) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        String alias = label.toLowerCase(Locale.ROOT);
        return name.equals("shop") || alias.equals("shop") || alias.equals("magasin") || alias.equals("store");
    }

    private String root(boolean money) {
        return money ? "shop" : "boutique";
    }

    private String mainTitle(boolean money) {
        String configured = plugin.getConfig().getString(root(money) + ".title");
        if (configured != null && !configured.isEmpty()) return CC.color(configured);
        return CC.color(money ? "&8Shop &7- &aMoney" : "&8Boutique &7- &6Tokens");
    }

    private int buttonSlot(boolean money, String button, int fallback) {
        return plugin.getConfig().getInt(root(money) + ".buttons." + button + ".slot", fallback);
    }

    private boolean buttonEnabled(boolean money, String button) {
        return plugin.getConfig().getBoolean(root(money) + ".buttons." + button + ".enabled", true);
    }

    private List<Integer> slots(boolean money, String key, List<Integer> fallback) {
        List<Integer> configured = plugin.getConfig().getIntegerList(root(money) + "." + key);
        return configured == null || configured.isEmpty() ? fallback : configured;
    }

    private List<Integer> categorySlots(boolean money) {
        return slots(money, "category-slots", Arrays.asList(10, 12, 14, 16, 28, 30, 32, 34));
    }

    private List<Integer> itemSlots(boolean money) {
        return slots(money, "item-slots", Arrays.asList(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        ));
    }

    private void openMain(Player player, boolean money, int page) {
        String base = root(money);
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection(base + ".categories");
        List<String> ids = categories == null ? Collections.<String>emptyList() : new ArrayList<String>(categories.getKeys(false));
        List<Integer> categorySlots = categorySlots(money);
        int perPage = categorySlots.size();
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) perPage));
        page = Math.max(0, Math.min(page, pages - 1));

        MenuHolder holder = new MenuHolder(money, null, false, page);
        Inventory inv = Bukkit.createInventory(holder, 54, mainTitle(money));
        holder.inventory = inv;
        fill(inv);

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < ids.size(); i++) {
            String id = ids.get(start + i);
            ConfigurationSection category = categories.getConfigurationSection(id);
            if (category != null) inv.setItem(categorySlots.get(i), categoryIcon(category, id, money));
        }

        inv.setItem(4, infoItem(player, money, page, pages));
        putButton(inv, player, money, "back", 45, Material.ARROW, "&c&lRetour", "&7Retour / fermer le menu.");
        putButton(inv, player, money, "previous", 47, Material.ARROW, "&e&lPage précédente", "&7Retourner à la page précédente.");
        putButton(inv, player, money, "balance", 48, money ? Material.EMERALD : Material.GOLD_INGOT,
                money ? "&a&lArgent (Shop)" : "&6&lTokens (Boutique)",
                money ? "&7Solde: &a" + plugin.economy().format(player) + " &8| &7/shop" : "&7Solde: &6" + plugin.tokens().get(player.getUniqueId()) + " tokens &8| &7/boutique");
        putButton(inv, player, money, "close", 49, Material.BARRIER, "&c&lFermer", "&7Fermer le menu.");
        putButton(inv, player, money, "next", 53, Material.ARROW, "&a&lPage suivante", "&7Aller à la page suivante.");

        player.openInventory(inv);
    }

    private void openCategory(Player player, boolean money, String categoryId, int page) {
        String base = root(money);
        String categoryPath = base + ".categories." + categoryId;
        ConfigurationSection category = plugin.getConfig().getConfigurationSection(categoryPath);
        if (category == null) {
            openMain(player, money, 0);
            return;
        }

        List<String> ids = getItemIds(category);
        List<Integer> itemSlots = itemSlots(money);
        int perPage = itemSlots.size();
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) perPage));
        page = Math.max(0, Math.min(page, pages - 1));

        String configuredTitle = category.getString("menu-title");
        String title = configuredTitle != null && !configuredTitle.isEmpty()
                ? configuredTitle
                : (money ? "&8Shop &7» " : "&8Boutique &7» ") + category.getString("name", categoryId);

        MenuHolder holder = new MenuHolder(money, categoryId, true, page);
        Inventory inv = Bukkit.createInventory(holder, 54, CC.color(title));
        holder.inventory = inv;
        fill(inv);

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < ids.size(); i++) {
            String id = ids.get(start + i);
            ConfigurationSection entry = getItemSection(category, base, id);
            if (entry != null) inv.setItem(itemSlots.get(i), displayEntry(entry, id, money, player));
        }

        inv.setItem(4, infoItem(player, money, page, pages));
        putButton(inv, player, money, "back", 45, Material.ARROW, "&c&lRetour", "&7Retour aux catégories.");
        putButton(inv, player, money, "previous", 47, Material.ARROW, "&e&lPage précédente", "&7Retourner à la page précédente.");
        putButton(inv, player, money, "balance", 48, money ? Material.EMERALD : Material.GOLD_INGOT,
                money ? "&a&lArgent (Shop)" : "&6&lTokens (Boutique)",
                money ? "&7Solde: &a" + plugin.economy().format(player) + " &8| &7/shop" : "&7Solde: &6" + plugin.tokens().get(player.getUniqueId()) + " tokens &8| &7/boutique");
        putButton(inv, player, money, "close", 49, Material.BARRIER, "&c&lFermer", "&7Fermer le menu.");
        putButton(inv, player, money, "next", 53, Material.ARROW, "&a&lPage suivante", "&7Aller à la page suivante.");

        player.openInventory(inv);
    }

    private List<String> getItemIds(ConfigurationSection category) {
        ConfigurationSection itemSection = category.getConfigurationSection("items");
        if (itemSection != null) return new ArrayList<String>(itemSection.getKeys(false));
        return category.getStringList("items");
    }

    private ConfigurationSection getItemSection(ConfigurationSection category, String base, String id) {
        ConfigurationSection nested = category.getConfigurationSection("items." + id);
        if (nested != null) return nested;
        return plugin.getConfig().getConfigurationSection(base + ".items." + id);
    }

    private ItemStack infoItem(Player player, boolean money, int page, int pages) {
        String name = plugin.getConfig().getString(root(money) + ".information.name", money ? "&a&lSHOP" : "&6&lBOUTIQUE");
        String materialName = plugin.getConfig().getString(root(money) + ".information.material", money ? "EMERALD" : "GOLD_INGOT");
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = money ? Material.EMERALD : Material.GOLD_INGOT;
        List<String> lore = plugin.getConfig().getStringList(root(money) + ".information.lore");
        if (lore.isEmpty()) {
            lore = new ArrayList<String>();
            lore.add(money ? "&7Achète des articles avec ton argent (/shop)." : "&7Débloque des avantages avec tes tokens (/boutique).");
        }
        lore = applyPlaceholders(player, money, lore);
        lore.add("");
        lore.add(money ? "&eArgent: &a" + plugin.economy().format(player) : "&eTokens: &6" + plugin.tokens().get(player.getUniqueId()));
        lore.add("&7Page &f" + (page + 1) + "&7/&f" + pages);
        return new ItemBuilder(material).name(name).lore(lore).build();
    }

    private void putButton(Inventory inv, Player player, boolean money, String id, int fallbackSlot, Material fallbackMaterial, String fallbackName, String fallbackLore) {
        if (!buttonEnabled(money, id)) return;
        String base = root(money) + ".buttons." + id;
        int slot = buttonSlot(money, id, fallbackSlot);
        String materialName = plugin.getConfig().getString(base + ".material", fallbackMaterial.name());
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = fallbackMaterial;
        String name = plugin.getConfig().getString(base + ".name", fallbackName);
        List<String> lore = plugin.getConfig().getStringList(base + ".lore");
        if (lore.isEmpty()) lore = Collections.singletonList(fallbackLore);
        lore = applyPlaceholders(player, money, lore);
        if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, new ItemBuilder(material).name(name).lore(lore).build());
    }

    private List<String> applyPlaceholders(Player player, boolean money, List<String> lore) {
        List<String> out = new ArrayList<String>();
        String moneyText = plugin.economy().format(player);
        String tokensText = String.valueOf(plugin.tokens().get(player.getUniqueId()));
        for (String line : lore) {
            out.add(line.replace("%money%", moneyText).replace("%tokens%", tokensText)
                    .replace("%currency%", money ? "argent" : "tokens")
                    .replace("%store%", money ? "/shop" : "/boutique"));
        }
        return out;
    }

    private ItemStack categoryIcon(ConfigurationSection sec, String id, boolean money) {
        Material material = Material.matchMaterial(sec.getString("material", money ? "CHEST" : "NETHER_STAR"));
        if (material == null) material = Material.CHEST;
        List<String> lore = new ArrayList<String>(sec.getStringList("lore"));
        lore.add("");
        lore.add("&eClique pour ouvrir.");
        return new ItemBuilder(material).name(sec.getString("name", "&f" + id)).lore(lore).build();
    }

    private ItemStack displayEntry(ConfigurationSection sec, String id, boolean money, Player player) {
        String type = sec.getString("type", "ITEM").toUpperCase(Locale.ROOT);
        if ("RANKUP".equals(type)) {
            return plugin.rankup().buildShopIcon(player, sec, id, money);
        }
        if ("ATOUT".equals(type)) {
            Material material = Material.matchMaterial(sec.getString("material", "PAPER"));
            if (material == null) material = Material.PAPER;
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            meta.setDisplayName(CC.color(sec.getString("name", id)));
            List<String> lore = new ArrayList<String>(sec.getStringList("lore"));
            String atoutId = sec.getString("atout", id).toUpperCase(Locale.ROOT);
            boolean owned = plugin.data().getList(player.getUniqueId(), "atouts_owned").contains(atoutId);
            lore.add("");
            lore.add(owned ? "&a✔ Déjà débloqué" : "&7Prix: " + priceLine(sec, false));
            lore.add(owned ? "&8Utilise &e/atouts &8pour l'activer." : "&eClique pour acheter.");
            meta.setLore(CC.color(lore));
            stack.setItemMeta(meta);
            return stack;
        }
        if ("KIT".equals(type)) {
            return plugin.kits().buildShopIcon(player, sec, id, money);
        }

        String itemId = sec.getString("item", id);
        ItemStack stack = plugin.items().create(itemId);
        if (stack == null) return new ItemBuilder(Material.BARRIER).name("&cItem inconnu: " + itemId).lore("&7Vérifie items.yml").build();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
            List<String> configuredLore = sec.getStringList("lore");
            if (!configuredLore.isEmpty()) {
                lore.add("");
                lore.addAll(configuredLore);
            }
            lore.add("");
            lore.add("&7Prix: " + priceLine(sec, money));
            lore.add("&eClique pour acheter.");
            meta.setLore(CC.color(lore));
            stack.setItemMeta(meta);
        }
        int amount = Math.max(1, sec.getInt("amount", 1));
        stack.setAmount(Math.min(64, amount));
        return stack;
    }

    private String priceLine(ConfigurationSection sec, boolean money) {
        return money
                ? "&a" + plugin.economy().format(sec.getDouble("price", 0.0)) + " &7(argent)"
                : "&6" + sec.getLong("price", 0L) + " tokens";
    }

    private String trimDouble(double value) {
        if (value == Math.rint(value)) return String.valueOf((long) value);
        return String.format(Locale.US, "%.2f", value);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        Player player = (Player) event.getWhoClicked();
        MenuHolder holder = (MenuHolder) event.getView().getTopInventory().getHolder();
        int slot = event.getRawSlot();
        int back = buttonSlot(holder.money, "back", 45);
        int previous = buttonSlot(holder.money, "previous", 47);
        int close = buttonSlot(holder.money, "close", 49);
        int next = buttonSlot(holder.money, "next", 53);

        if (slot == close) {
            player.closeInventory();
            return;
        }

        if (slot == back) {
            if (holder.categoryMenu) openMain(player, holder.money, 0);
            else player.closeInventory();
            return;
        }

        if (slot == previous) {
            if (holder.page > 0) {
                if (holder.categoryMenu) openCategory(player, holder.money, holder.categoryId, holder.page - 1);
                else openMain(player, holder.money, holder.page - 1);
            }
            return;
        }

        if (slot == next) {
            if (holder.categoryMenu) {
                int max = pageCountForCategory(holder.money, holder.categoryId);
                if (holder.page + 1 < max) openCategory(player, holder.money, holder.categoryId, holder.page + 1);
            } else {
                int max = pageCountForCategories(holder.money);
                if (holder.page + 1 < max) openMain(player, holder.money, holder.page + 1);
            }
            return;
        }

        if (!holder.categoryMenu) {
            ConfigurationSection categories = plugin.getConfig().getConfigurationSection(root(holder.money) + ".categories");
            if (categories == null) return;
            List<String> ids = new ArrayList<String>(categories.getKeys(false));
            List<Integer> slots = categorySlots(holder.money);
            int index = slots.indexOf(slot);
            if (index < 0) return;
            int absolute = holder.page * slots.size() + index;
            if (absolute < 0 || absolute >= ids.size()) return;
            openCategory(player, holder.money, ids.get(absolute), 0);
            return;
        }

        List<Integer> slots = itemSlots(holder.money);
        int index = slots.indexOf(slot);
        if (index < 0) return;
        ConfigurationSection category = plugin.getConfig().getConfigurationSection(root(holder.money) + ".categories." + holder.categoryId);
        if (category == null) return;
        List<String> ids = getItemIds(category);
        int absolute = holder.page * slots.size() + index;
        if (absolute < 0 || absolute >= ids.size()) return;

        String id = ids.get(absolute);
        ConfigurationSection entry = getItemSection(category, root(holder.money), id);
        if (entry == null) return;
        buy(player, entry, id, holder.money);
        openCategory(player, holder.money, holder.categoryId, holder.page);
    }

    private void buy(Player player, ConfigurationSection entry, String id, boolean money) {
        String type = entry.getString("type", "ITEM").toUpperCase(Locale.ROOT);
        if ("RANKUP".equals(type)) {
            plugin.rankup().buyFromShop(player, entry, money);
            return;
        }
        if ("ATOUT".equals(type)) {
            String atoutId = entry.getString("atout", id).toUpperCase(Locale.ROOT);
            if (plugin.data().getList(player.getUniqueId(), "atouts_owned").contains(atoutId)) {
                plugin.msg(player, "&eTu possèdes déjà cet atout. &7Utilise &e/atouts &7pour l'activer.");
                return;
            }
        }
        if ("KIT".equals(type)) {
            String kitId = entry.getString("kit", id).toUpperCase(Locale.ROOT);
            if (plugin.kits().ownsKit(player.getUniqueId(), kitId)) {
                plugin.msg(player, "&eTu possèdes déjà ce kit. &7Utilise &e/kit&7.");
                return;
            }
        }

        if (money) {
            double price = entry.getDouble("price", 0.0);
            if (price <= 0 || !plugin.economy().withdraw(player, price)) {
                plugin.msg(player, "&cPas assez d'argent. &7Prix: &a" + plugin.economy().format(price) + " &8(/shop)");
                return;
            }
            if (!give(player, entry, id, type)) {
                plugin.economy().deposit(player, price);
                plugin.msg(player, "&cAchat impossible: article mal configuré. Ton argent a été rendu.");
                return;
            }
            plugin.msg(player, "&aAchat shop effectué pour &f" + plugin.economy().format(price) + "&a.");
        } else {
            long price = entry.getLong("price", 0L);
            if (price <= 0 || !plugin.tokens().take(player.getUniqueId(), price)) {
                plugin.msg(player, "&cPas assez de tokens. &7Prix: &6" + price + " &8(/boutique)");
                return;
            }
            if (!give(player, entry, id, type)) {
                plugin.tokens().add(player.getUniqueId(), price);
                plugin.msg(player, "&cAchat impossible: article mal configuré. Tes tokens ont été rendus.");
                return;
            }
            plugin.msg(player, "&aAchat boutique effectué pour &6" + price + " tokens&a.");
        }
    }

    private boolean give(Player player, ConfigurationSection entry, String id, String type) {
        if ("ATOUT".equals(type)) {
            String atoutId = entry.getString("atout", id).toUpperCase(Locale.ROOT);
            List<String> owned = plugin.data().getList(player.getUniqueId(), "atouts_owned");
            if (owned.contains(atoutId)) return false;
            owned.add(atoutId);
            plugin.data().setList(player.getUniqueId(), "atouts_owned", owned);
            plugin.msg(player, "&aTu as débloqué l'atout &e" + atoutId + "&a. Utilise &e/atouts &apour l'activer.");
            return true;
        }
        if ("KIT".equals(type)) {
            String kitId = entry.getString("kit", id).toUpperCase(Locale.ROOT);
            if (plugin.kits().ownsKit(player.getUniqueId(), kitId)) {
                return false;
            }
            if (!plugin.kits().unlockKit(player.getUniqueId(), kitId)) {
                return false;
            }
            plugin.kits().sendPurchasedMessage(player, kitId);
            return true;
        }

        String itemId = entry.getString("item", id);
        ItemStack item = plugin.items().create(itemId);
        if (item == null) return false;
        int amount = Math.max(1, entry.getInt("amount", 1));
        item.setAmount(Math.min(64, amount));
        java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack left : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), left);
        return true;
    }

    private int pageCountForCategories(boolean money) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(root(money) + ".categories");
        int count = section == null ? 0 : section.getKeys(false).size();
        return Math.max(1, (int) Math.ceil(count / (double) categorySlots(money).size()));
    }

    private int pageCountForCategory(boolean money, String categoryId) {
        ConfigurationSection category = plugin.getConfig().getConfigurationSection(root(money) + ".categories." + categoryId);
        int count = category == null ? 0 : getItemIds(category).size();
        return Math.max(1, (int) Math.ceil(count / (double) itemSlots(money).size()));
    }

    private void fill(Inventory inv) {
        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).name("&r").build();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private static class MenuHolder implements InventoryHolder {
        private final boolean money;
        private final String categoryId;
        private final boolean categoryMenu;
        private final int page;
        private Inventory inventory;

        private MenuHolder(boolean money, String categoryId, boolean categoryMenu, int page) {
            this.money = money;
            this.categoryId = categoryId;
            this.categoryMenu = categoryMenu;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
