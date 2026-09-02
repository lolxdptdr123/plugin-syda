package fr.sydaria.rankup;

import fr.sydaria.Sydaria;
import fr.sydaria.util.CC;
import fr.sydaria.util.ItemBuilder;
import net.milkbowl.vault.permission.Permission;
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
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Système de rankup : progression de grade achetable avec l'argent (/rankup, /shop)
 * ou les tokens (/boutique). Les grades sont appliqués via LuckPerms (ou Vault en secours).
 */
public class RankUpManager implements CommandExecutor, Listener {
    private static final Map<String, Double> DEFAULT_COSTS = new HashMap<String, Double>();

    static {
        DEFAULT_COSTS.put("chevalier", 50000.0);
        DEFAULT_COSTS.put("marquis", 150000.0);
        DEFAULT_COSTS.put("seigneur", 500000.0);
        DEFAULT_COSTS.put("empereur", 1500000.0);
        DEFAULT_COSTS.put("supreme", 5000000.0);
        DEFAULT_COSTS.put("star", 15000000.0);
    }

    private final Sydaria plugin;
    private Permission vaultPerm;

    public RankUpManager(Sydaria plugin) {
        this.plugin = plugin;
        hookVault();
        if (!plugin.getConfig().isConfigurationSection("rankup.ranks")) {
            plugin.getLogger().warning("Section rankup.ranks absente de config.yml — prix par défaut utilisés. "
                    + "Ajoute la section rankup du jar dans ton config.yml puis /sydaria reload.");
        }
    }

    private void hookVault() {
        try {
            RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);
            if (rsp != null) {
                vaultPerm = rsp.getProvider();
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("rankup.enabled", true);
    }

    public List<String> ladder() {
        List<String> configured = plugin.getConfig().getStringList("rankup.ladder");
        if (configured == null || configured.isEmpty()) {
            return defaultLadder();
        }
        List<String> out = new ArrayList<String>();
        for (String entry : configured) {
            out.add(entry.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private List<String> defaultLadder() {
        return new ArrayList<String>(java.util.Arrays.asList(
                "default", "chevalier", "marquis", "seigneur", "empereur", "supreme", "star"
        ));
    }

    public int ladderIndex(String group) {
        if (group == null) {
            return -1;
        }
        return ladder().indexOf(group.toLowerCase(Locale.ROOT));
    }

    public String normalizeGroup(String group) {
        if (group == null || group.isEmpty()) {
            return "default";
        }
        String lower = group.toLowerCase(Locale.ROOT);
        // Alias courants du grade de base LuckPerms
        if ("default_player".equals(lower) || "joueur".equals(lower)
                || "player".equals(lower) || "member".equals(lower)
                || "membre".equals(lower) || "defaultplayer".equals(lower)) {
            return "default";
        }
        return lower;
    }

    public String currentRank(Player player) {
        String group = normalizeGroup(plugin.grades().group(player));
        if (ladderIndex(group) >= 0) {
            return group;
        }
        // Groupe hors ladder → début de la progression (évite "grade max" à tort)
        List<String> ladder = ladder();
        return ladder.isEmpty() ? "default" : ladder.get(0);
    }

    public String nextRank(Player player) {
        return nextRank(currentRank(player));
    }

    public String nextRank(String current) {
        List<String> ladder = ladder();
        int index = ladderIndex(normalizeGroup(current));
        if (index < 0) {
            index = 0;
        }
        if (index + 1 >= ladder.size()) {
            return null;
        }
        return ladder.get(index + 1);
    }

    public String previousRank(String rank) {
        List<String> ladder = ladder();
        int index = ladderIndex(normalizeGroup(rank));
        if (index <= 0) {
            return null;
        }
        return ladder.get(index - 1);
    }

    public double getCost(String rank) {
        String key = normalizeGroup(rank);
        String path = "rankup.ranks." + key + ".cost";
        if (plugin.getConfig().contains(path)) {
            return Math.max(0.0, plugin.getConfig().getDouble(path));
        }
        Double fallback = DEFAULT_COSTS.get(key);
        return fallback != null ? fallback : 0.0;
    }

    public String getCurrency(String rank) {
        String key = normalizeGroup(rank);
        String path = "rankup.ranks." + key + ".currency";
        if (plugin.getConfig().contains(path)) {
            String currency = plugin.getConfig().getString(path, "money");
            return currency == null ? "money" : currency.toLowerCase(Locale.ROOT);
        }
        return "money";
    }

    public boolean usesMoney(String rank) {
        return !"tokens".equals(getCurrency(rank));
    }

    public String displayName(String rank) {
        String mapped = plugin.getConfig().getString("scoreboard.grades." + normalizeGroup(rank));
        if (mapped != null && !mapped.isEmpty()) {
            return CC.color(mapped);
        }
        String raw = normalizeGroup(rank);
        if (raw.isEmpty() || "default".equals(raw)) {
            return CC.color(plugin.getConfig().getString("scoreboard.grade-default", "Joueur"));
        }
        return raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1);
    }

    public String priceText(String rank) {
        if (usesMoney(rank)) {
            return "&a" + plugin.economy().format(getCost(rank)) + " &7(argent)";
        }
        return "&6" + (long) getCost(rank) + " tokens";
    }

    public boolean canRankUpTo(Player player, String targetRank) {
        if (!isEnabled()) {
            return false;
        }
        targetRank = normalizeGroup(targetRank);
        int targetIndex = ladderIndex(targetRank);
        if (targetIndex <= 0) {
            return false;
        }
        int currentIndex = ladderIndex(currentRank(player));
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        // Uniquement le grade immédiatement suivant
        return currentIndex + 1 == targetIndex;
    }

    public boolean canRankUp(Player player) {
        String next = nextRank(player);
        return next != null && canRankUpTo(player, next);
    }

    public boolean hasBalance(Player player, String targetRank) {
        double cost = getCost(targetRank);
        if (cost <= 0) {
            return false;
        }
        if (usesMoney(targetRank)) {
            return plugin.economy().has(player, cost);
        }
        return plugin.tokens().get(player.getUniqueId()) >= (long) cost;
    }

    public boolean withdraw(Player player, String targetRank) {
        double cost = getCost(targetRank);
        if (cost <= 0) {
            return false;
        }
        if (usesMoney(targetRank)) {
            return plugin.economy().withdraw(player, cost);
        }
        return plugin.tokens().take(player.getUniqueId(), (long) cost);
    }

    public void refund(Player player, String targetRank) {
        double cost = getCost(targetRank);
        if (cost <= 0) {
            return;
        }
        if (usesMoney(targetRank)) {
            plugin.economy().deposit(player, cost);
        } else {
            plugin.tokens().add(player.getUniqueId(), (long) cost);
        }
    }

    public boolean setGroup(Player player, String group) {
        group = normalizeGroup(group);
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player.getName() + " parent set " + group);
        }
        if (vaultPerm == null) {
            hookVault();
        }
        if (vaultPerm != null && vaultPerm.hasGroupSupport()) {
            try {
                String current = vaultPerm.getPrimaryGroup(player);
                if (current != null && !current.isEmpty() && !current.equalsIgnoreCase(group)) {
                    vaultPerm.playerRemoveGroup(player, current);
                }
                return vaultPerm.playerAddGroup(player, group);
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public boolean rankUp(Player player) {
        String next = nextRank(player);
        if (next == null) {
            plugin.msg(player, "&cTu es déjà au grade maximum.");
            return false;
        }
        return rankUpTo(player, next);
    }

    public boolean rankUpTo(Player player, String targetRank) {
        targetRank = normalizeGroup(targetRank);
        if (!isEnabled()) {
            plugin.msg(player, "&cLe système de rankup est désactivé.");
            return false;
        }
        if (!canRankUpTo(player, targetRank)) {
            String required = previousRank(targetRank);
            int currentIndex = ladderIndex(currentRank(player));
            int targetIndex = ladderIndex(targetRank);
            if (required == null || targetIndex <= 0) {
                plugin.msg(player, "&cCe grade ne peut pas être acheté.");
            } else if (currentIndex >= targetIndex) {
                plugin.msg(player, "&cTu possèdes déjà ce grade ou un grade supérieur.");
            } else {
                plugin.msg(player, "&cTu dois d'abord être &e" + displayName(required) + " &cpour acheter &e" + displayName(targetRank) + "&c.");
            }
            return false;
        }
        double cost = getCost(targetRank);
        if (cost <= 0) {
            plugin.msg(player, "&cPrix invalide pour ce grade. Vérifie &erankup.ranks." + targetRank + ".cost &cdans config.yml.");
            return false;
        }
        if (!hasBalance(player, targetRank)) {
            plugin.msg(player, "&cPas assez de " + (usesMoney(targetRank) ? "argent" : "tokens") + ". &7Prix: " + priceText(targetRank));
            return false;
        }
        if (!withdraw(player, targetRank)) {
            plugin.msg(player, "&cPaiement impossible.");
            return false;
        }
        if (!setGroup(player, targetRank)) {
            refund(player, targetRank);
            plugin.msg(player, "&cImpossible d'appliquer le grade. Contacte un admin (LuckPerms requis).");
            return false;
        }

        plugin.msg(player, "&aFélicitations ! Tu es maintenant &e" + displayName(targetRank) + "&a !");
        Bukkit.broadcastMessage(CC.color(plugin.prefix() + "&e" + player.getName() + " &7est passé au grade &e" + displayName(targetRank) + "&7 !"));
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (plugin.scoreboard() != null) {
                    plugin.scoreboard().update(player);
                }
            }
        }, 5L);
        return true;
    }

    public boolean buyFromShop(Player player, ConfigurationSection entry, boolean moneyShop) {
        String targetRank = entry.getString("rank");
        if (targetRank == null || targetRank.isEmpty()) {
            return false;
        }
        targetRank = normalizeGroup(targetRank);
        if (!canRankUpTo(player, targetRank)) {
            String required = previousRank(targetRank);
            if (required != null && !required.equals(currentRank(player))) {
                plugin.msg(player, "&cTu dois d'abord être &e" + displayName(required) + " &cpour acheter ce grade.");
            } else if (ladderIndex(currentRank(player)) >= ladderIndex(targetRank)) {
                plugin.msg(player, "&cTu possèdes déjà ce grade ou un grade supérieur.");
            } else {
                plugin.msg(player, "&cTu ne peux pas acheter ce grade pour le moment.");
            }
            return false;
        }

        double shopPrice = moneyShop ? entry.getDouble("price", 0.0) : entry.getLong("price", 0L);
        if (shopPrice <= 0) {
            plugin.msg(player, "&cArticle mal configuré.");
            return false;
        }

        if (moneyShop) {
            if (!plugin.economy().withdraw(player, shopPrice)) {
                plugin.msg(player, "&cPas assez d'argent. &7Prix: &a" + plugin.economy().format(shopPrice));
                return false;
            }
        } else if (!plugin.tokens().take(player.getUniqueId(), (long) shopPrice)) {
            plugin.msg(player, "&cPas assez de tokens. &7Prix: &6" + (long) shopPrice);
            return false;
        }

        if (!setGroup(player, targetRank)) {
            if (moneyShop) {
                plugin.economy().deposit(player, shopPrice);
            } else {
                plugin.tokens().add(player.getUniqueId(), (long) shopPrice);
            }
            plugin.msg(player, "&cImpossible d'appliquer le grade. Ton paiement a été rendu.");
            return false;
        }

        plugin.msg(player, "&aFélicitations ! Tu es maintenant &e" + displayName(targetRank) + "&a !");
        Bukkit.broadcastMessage(CC.color(plugin.prefix() + "&e" + player.getName() + " &7est passé au grade &e" + displayName(targetRank) + "&7 !"));
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (plugin.scoreboard() != null) {
                    plugin.scoreboard().update(player);
                }
            }
        }, 5L);
        return true;
    }

    public ItemStack buildShopIcon(Player player, ConfigurationSection entry, String id, boolean moneyShop) {
        String targetRank = normalizeGroup(entry.getString("rank", id));
        Material material = Material.matchMaterial(entry.getString("material", "GOLD_HELMET"));
        if (material == null) {
            material = Material.GOLD_HELMET;
        }
        List<String> lore = new ArrayList<String>(entry.getStringList("lore"));
        lore.add("");
        lore.add("&7Grade: &e" + displayName(targetRank));
        lore.add("&7Prix: " + (moneyShop
                ? "&a" + plugin.economy().format(entry.getDouble("price", 0.0)) + " &7(argent)"
                : "&6" + entry.getLong("price", 0L) + " tokens"));
        lore.add("");
        if (ladderIndex(currentRank(player)) >= ladderIndex(targetRank)) {
            lore.add("&a✔ Grade déjà obtenu");
        } else if (canRankUpTo(player, targetRank)) {
            lore.add("&eClique pour acheter.");
        } else {
            String required = previousRank(targetRank);
            lore.add(required != null ? "&cRequis: &e" + displayName(required) : "&cIndisponible");
        }
        return new ItemBuilder(material).name(entry.getString("name", "&eRankup " + displayName(targetRank))).lore(lore).build();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cJoueur uniquement.");
            return true;
        }
        Player player = (Player) sender;
        if (!isEnabled()) {
            plugin.msg(player, "&cLe rankup est désactivé.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            sendInfo(player);
            return true;
        }

        if (plugin.getConfig().getBoolean("rankup.gui.enabled", true) && args.length == 0) {
            openGui(player);
            return true;
        }

        rankUp(player);
        return true;
    }

    private void sendInfo(Player player) {
        String current = currentRank(player);
        String next = nextRank(player);
        plugin.msg(player, "&6&lRankup &7» &fInformations");
        plugin.msg(player, "&7Grade actuel: &e" + displayName(current));
        if (next == null) {
            plugin.msg(player, "&7Prochain grade: &cAucun (grade max)");
            return;
        }
        plugin.msg(player, "&7Prochain grade: &e" + displayName(next));
        plugin.msg(player, "&7Prix: " + priceText(next));
        plugin.msg(player, "&7Utilise &e/rankup &7pour acheter.");
    }

    public void openGui(Player player) {
        String title = CC.color(plugin.getConfig().getString("rankup.gui.title", "&8&lRankup &7» &aGrades"));
        GuiHolder holder = new GuiHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.inventory = inv;
        fill(inv);

        List<String> ladder = ladder();
        List<Integer> slots = plugin.getConfig().getIntegerList("rankup.gui.slots");
        if (slots == null || slots.isEmpty()) {
            slots = java.util.Arrays.asList(10, 12, 14, 16, 21, 23, 25, 31, 33);
        }

        String current = currentRank(player);
        int currentIndex = ladderIndex(current);

        for (int i = 1; i < ladder.size() && i - 1 < slots.size(); i++) {
            String rank = ladder.get(i);
            int slot = slots.get(i - 1);
            inv.setItem(slot, rankIcon(player, rank, currentIndex, i));
        }

        inv.setItem(4, new ItemBuilder(Material.BOOK)
                .name("&6&lRankup")
                .lore(
                        "&7Grade actuel: &e" + displayName(current),
                        nextRank(player) != null ? "&7Prochain: &e" + displayName(nextRank(player)) + " &7(" + priceText(nextRank(player)) + ")" : "&7Grade maximum atteint.",
                        "",
                        "&eClique sur ton prochain grade",
                        "&epour rankup instantanément."
                ).build());

        inv.setItem(49, new ItemBuilder(Material.EMERALD)
                .name("&a&lRankup maintenant")
                .lore(
                        nextRank(player) != null ? "&7Coût: " + priceText(nextRank(player)) : "&cGrade maximum.",
                        "",
                        "&eClique pour acheter le prochain grade."
                ).build());

        player.openInventory(inv);
    }

    private ItemStack rankIcon(Player player, String rank, int currentIndex, int rankIndex) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("rankup.gui.rank-material." + rank, "GOLD_HELMET"));
        if (material == null) {
            material = Material.GOLD_HELMET;
        }

        List<String> lore = new ArrayList<String>();
        lore.add("&7Prix: " + priceText(rank));
        lore.add("&7Devise: " + (usesMoney(rank) ? "&aArgent" : "&6Tokens"));
        lore.add("");

        if (currentIndex >= rankIndex) {
            lore.add("&a✔ Grade obtenu");
            material = Material.matchMaterial("EMERALD_BLOCK");
            if (material == null) {
                material = Material.EMERALD;
            }
        } else if (currentIndex + 1 == rankIndex) {
            lore.add("&eClique pour rankup !");
        } else {
            lore.add("&cGrade verrouillé");
            material = Material.matchMaterial("COAL_BLOCK");
            if (material == null) {
                material = Material.COAL;
            }
        }

        return new ItemBuilder(material).name("&e" + displayName(rank)).lore(lore).build();
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
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == 49) {
            rankUp(player);
            openGui(player);
            return;
        }

        List<String> ladder = ladder();
        List<Integer> slots = plugin.getConfig().getIntegerList("rankup.gui.slots");
        if (slots == null || slots.isEmpty()) {
            slots = java.util.Arrays.asList(10, 12, 14, 16, 21, 23, 25, 31, 33);
        }

        int index = slots.indexOf(slot);
        if (index < 0 || index + 1 >= ladder.size()) {
            return;
        }

        String targetRank = ladder.get(index + 1);
        if (rankUpTo(player, targetRank)) {
            openGui(player);
        }
    }

    private void fill(Inventory inv) {
        ItemStack filler = new ItemBuilder(Material.STAINED_GLASS_PANE, 1, (short) 15).name("&r").build();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private static class GuiHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
